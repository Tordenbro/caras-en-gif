package com.tordenbro.carasengif;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;
import com.google.mlkit.vision.segmentation.Segmentation;
import com.google.mlkit.vision.segmentation.SegmentationMask;
import com.google.mlkit.vision.segmentation.Segmenter;
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Recorta personas de una foto con ML Kit (todo en el móvil, sin internet):
 * - Selfie Segmentation: máscara de "persona" frente al fondo.
 * - Face Detection: caja y ojos de cada cara (para recortar la cabeza y para
 *   que el editor pueda colocar el recorte justo sobre los ojos).
 * Llamar siempre desde un hilo que no sea el principal (usa Tasks.await).
 */
final class Cutout {

    /** Un recorte: imagen con transparencia + dónde están los ojos (en unidades de distancia entre ojos). */
    static final class Piece {
        Bitmap bmp;
        boolean eyes;
        double wE;   // ancho del recorte / distancia entre ojos
        double eu;   // (ojo medio - centro del recorte) horizontal / distancia entre ojos
        double ev;   // ídem vertical
        double ang;  // inclinación de la línea de los ojos, en grados
    }

    private static final class FaceInfo {
        Rect box;
        PointF p1, p2; // ojo izquierdo y derecho de la IMAGEN (p1.x <= p2.x)
    }

    static final int MAX_SIDE = 1600;   // la foto se reduce a esto antes de analizarla
    static final int OUT_SIDE = 700;    // lado máximo de cada recorte

    private Segmenter segmenter;
    private FaceDetector detector;

    private synchronized Segmenter seg() {
        if (segmenter == null) {
            segmenter = Segmentation.getClient(new SelfieSegmenterOptions.Builder()
                    .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                    .build());
        }
        return segmenter;
    }

    private synchronized FaceDetector det() {
        if (detector == null) {
            detector = FaceDetection.getClient(new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setMinFaceSize(0.04f)
                    .build());
        }
        return detector;
    }

    private static float smooth(float a, float b, float x) {
        float t = (x - a) / (b - a);
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return t * t * (3f - 2f * t);
    }

    /** Opacidad 0..1 de "persona" para cada píxel de la imagen (con borde suave). */
    private float[] personAlpha(InputImage img, int w, int h) throws Exception {
        SegmentationMask m = Tasks.await(seg().process(img));
        ByteBuffer b = m.getBuffer();
        b.rewind();
        int mw = m.getWidth(), mh = m.getHeight();
        float[] conf = new float[mw * mh];
        FloatBuffer fb = b.asFloatBuffer();
        fb.get(conf);
        float[] a = new float[w * h];
        for (int y = 0; y < h; y++) {
            int my = Math.min(mh - 1, (int) ((y + 0.5f) * mh / h));
            for (int x = 0; x < w; x++) {
                int mx = Math.min(mw - 1, (int) ((x + 0.5f) * mw / w));
                a[y * w + x] = smooth(0.4f, 0.8f, conf[my * mw + mx]);
            }
        }
        return a;
    }

    private List<FaceInfo> faces(InputImage img) throws Exception {
        List<Face> found = Tasks.await(det().process(img));
        List<FaceInfo> out = new ArrayList<>();
        for (Face f : found) {
            FaceInfo fi = new FaceInfo();
            fi.box = f.getBoundingBox();
            if (fi.box.width() < 8 || fi.box.height() < 8) continue;
            FaceLandmark l = f.getLandmark(FaceLandmark.LEFT_EYE);
            FaceLandmark r = f.getLandmark(FaceLandmark.RIGHT_EYE);
            if (l != null && r != null) {
                PointF a = l.getPosition(), c = r.getPosition();
                fi.p1 = a.x <= c.x ? a : c;
                fi.p2 = a.x <= c.x ? c : a;
            } else {
                float bw = fi.box.width(), bh = fi.box.height();
                fi.p1 = new PointF(fi.box.centerX() - 0.2f * bw, fi.box.top + 0.38f * bh);
                fi.p2 = new PointF(fi.box.centerX() + 0.2f * bw, fi.box.top + 0.38f * bh);
            }
            out.add(fi);
        }
        // de izquierda a derecha
        Collections.sort(out, (p, q) -> Integer.compare(p.box.centerX(), q.box.centerX()));
        return out;
    }

    /** Recorta cabezas (head=true) o personas enteras (head=false). La lista puede salir vacía. */
    List<Piece> run(Bitmap src, boolean head) throws Exception {
        Bitmap work = src;
        int mx = Math.max(src.getWidth(), src.getHeight());
        if (mx > MAX_SIDE) {
            float k = (float) MAX_SIDE / mx;
            work = Bitmap.createScaledBitmap(src, Math.max(1, Math.round(src.getWidth() * k)),
                    Math.max(1, Math.round(src.getHeight() * k)), true);
        }
        int w = work.getWidth(), h = work.getHeight();
        InputImage img = InputImage.fromBitmap(work, 0);
        List<FaceInfo> faces = faces(img);
        List<Piece> pieces = new ArrayList<>();
        if (head && faces.isEmpty()) return pieces;
        float[] a = personAlpha(img, w, h);
        int[] pix = new int[w * h];
        work.getPixels(pix, 0, w, 0, 0, w, h);
        if (head) cutHeads(a, pix, w, h, faces, pieces);
        else cutBodies(a, pix, w, h, faces, pieces);
        if (work != src) work.recycle();
        return pieces;
    }

    // ---------------------------------------------------------------- cabezas

    private void cutHeads(float[] a, int[] pix, int w, int h, List<FaceInfo> faces, List<Piece> out) {
        for (int i = 0; i < faces.size(); i++) {
            FaceInfo f = faces.get(i);
            float bw = f.box.width(), bh = f.box.height();
            float chin = f.box.bottom;
            int x0 = Math.max(0, (int) Math.floor(f.box.left - 0.6f * bw));
            int x1 = Math.min(w, (int) Math.ceil(f.box.right + 0.6f * bw));
            int y0 = Math.max(0, (int) Math.floor(f.box.top - 0.9f * bh));
            int y1 = Math.min(h, (int) Math.ceil(chin + 0.25f * bh));
            if (x1 - x0 < 4 || y1 - y0 < 4) continue;
            float cx = f.box.centerX(), cy = f.box.centerY() - 0.1f * bh;
            float[] al = new float[(x1 - x0) * (y1 - y0)];
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
            int count = 0;
            for (int y = y0; y < y1; y++) {
                // fundido del cuello: opaco hasta justo debajo de la barbilla, transparente 0,25·alto más abajo
                float fade = 1f - smooth(chin + 0.02f * bh, chin + 0.25f * bh, y);
                if (fade <= 0f) continue;
                for (int x = x0; x < x1; x++) {
                    float v = a[y * w + x];
                    if (v < 0.02f) continue;
                    if (faces.size() > 1 && !closestTo(i, faces, x, y, cx, cy, bw, bh)) continue;
                    v *= fade;
                    if (v < 0.02f) continue;
                    al[(y - y0) * (x1 - x0) + (x - x0)] = v;
                    count++;
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
            if (count < 0.15f * bw * bh) continue; // casi no hay persona en esa cara
            Piece p = build(pix, w, al, x0, y0, x1 - x0, minX, minY, maxX, maxY, f);
            if (p != null) out.add(p);
        }
    }

    /** ¿El píxel (x,y) está más cerca de la cara i que de cualquier otra? (distancia normalizada al tamaño de cada cara) */
    private static boolean closestTo(int i, List<FaceInfo> faces, int x, int y, float cx, float cy, float bw, float bh) {
        float dx = (x - cx) / bw, dy = (y - cy) / bh;
        float mine = dx * dx + dy * dy;
        for (int j = 0; j < faces.size(); j++) {
            if (j == i) continue;
            Rect o = faces.get(j).box;
            float ow = o.width(), oh = o.height();
            float ex = (x - o.centerX()) / ow, ey = (y - (o.centerY() - 0.1f * oh)) / oh;
            if (ex * ex + ey * ey < mine) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- personas enteras

    private void cutBodies(float[] a, int[] pix, int w, int h, List<FaceInfo> faces, List<Piece> out) {
        int n = w * h;
        int[] label = new int[n];
        int[] queue = new int[n];
        List<int[]> comps = new ArrayList<>(); // {area, minX, minY, maxX, maxY}
        for (int s = 0; s < n; s++) {
            if (label[s] != 0 || a[s] < 0.02f) continue;
            int id = comps.size() + 1;
            int[] c = {0, w, h, -1, -1};
            int qh = 0, qt = 0;
            queue[qt++] = s;
            label[s] = id;
            while (qh < qt) {
                int p = queue[qh++];
                int px = p % w, py = p / w;
                c[0]++;
                if (px < c[1]) c[1] = px;
                if (py < c[2]) c[2] = py;
                if (px > c[3]) c[3] = px;
                if (py > c[4]) c[4] = py;
                if (px > 0 && label[p - 1] == 0 && a[p - 1] >= 0.02f) { label[p - 1] = id; queue[qt++] = p - 1; }
                if (px < w - 1 && label[p + 1] == 0 && a[p + 1] >= 0.02f) { label[p + 1] = id; queue[qt++] = p + 1; }
                if (py > 0 && label[p - w] == 0 && a[p - w] >= 0.02f) { label[p - w] = id; queue[qt++] = p - w; }
                if (py < h - 1 && label[p + w] == 0 && a[p + w] >= 0.02f) { label[p + w] = id; queue[qt++] = p + w; }
            }
            comps.add(c);
        }
        int biggest = 0;
        for (int[] c : comps) biggest = Math.max(biggest, c[0]);
        if (biggest == 0) return;
        double minArea = Math.max(0.01 * n, 0.12 * biggest);
        List<Integer> keep = new ArrayList<>();
        for (int i = 0; i < comps.size(); i++) if (comps.get(i)[0] >= minArea) keep.add(i);
        Collections.sort(keep, (p, q) -> Integer.compare(comps.get(q)[0], comps.get(p)[0]));
        if (keep.size() > 6) keep = new ArrayList<>(keep.subList(0, 6));
        Collections.sort(keep, (p, q) -> Integer.compare(comps.get(p)[1], comps.get(q)[1])); // de izquierda a derecha
        for (int idx : keep) {
            int[] c = comps.get(idx);
            int id = idx + 1;
            int bw = c[3] - c[1] + 1, bh = c[4] - c[2] + 1;
            float[] al = new float[bw * bh];
            for (int y = c[2]; y <= c[4]; y++)
                for (int x = c[1]; x <= c[3]; x++)
                    if (label[y * w + x] == id) al[(y - c[2]) * bw + (x - c[1])] = a[y * w + x];
            // la cara más grande cuyo centro cae dentro de esta persona
            FaceInfo best = null;
            for (FaceInfo f : faces) {
                int fx = Math.max(0, Math.min(w - 1, f.box.centerX()));
                int fy = Math.max(0, Math.min(h - 1, f.box.centerY()));
                if (label[fy * w + fx] == id && (best == null || f.box.width() > best.box.width())) best = f;
            }
            Piece p = build(pix, w, al, c[1], c[2], bw, c[1], c[2], c[3], c[4], best);
            if (p != null) out.add(p);
        }
    }

    // ---------------------------------------------------------------- común

    /**
     * Crea el bitmap recortado.
     * al: opacidades de una región que empieza en (rx,ry) y tiene ancho rw; el recorte es [minX..maxX]x[minY..maxY].
     * face: cara de esta persona (o null) para guardar dónde están los ojos.
     */
    private Piece build(int[] pix, int w, float[] al, int rx, int ry, int rw,
                        int minX, int minY, int maxX, int maxY, FaceInfo face) {
        int bw = maxX - minX + 1, bh = maxY - minY + 1;
        if (bw < 4 || bh < 4) return null;
        int[] px = new int[bw * bh];
        for (int y = 0; y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                float v = al[(minY + y - ry) * rw + (minX + x - rx)];
                int col = pix[(minY + y) * w + (minX + x)] & 0x00FFFFFF;
                px[y * bw + x] = (Math.round(v * 255f) << 24) | col;
            }
        }
        Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        bmp.setPixels(px, 0, bw, 0, 0, bw, bh);
        Piece p = new Piece();
        if (face != null) {
            double E = Math.hypot(face.p2.x - face.p1.x, face.p2.y - face.p1.y);
            if (E >= 4) {
                double mx = (face.p1.x + face.p2.x) / 2.0 - minX;
                double my = (face.p1.y + face.p2.y) / 2.0 - minY;
                p.eyes = true;
                p.wE = bw / E;
                p.eu = (mx - bw / 2.0) / E;
                p.ev = (my - bh / 2.0) / E;
                p.ang = Math.toDegrees(Math.atan2(face.p2.y - face.p1.y, face.p2.x - face.p1.x));
            }
        }
        int big = Math.max(bw, bh);
        if (big > OUT_SIDE) {
            float k = (float) OUT_SIDE / big;
            Bitmap s = Bitmap.createScaledBitmap(bmp, Math.max(1, Math.round(bw * k)), Math.max(1, Math.round(bh * k)), true);
            bmp.recycle();
            bmp = s;
        }
        p.bmp = bmp;
        return p;
    }
}

package com.github.argszero.guahaocode;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by shaoaq on 8/11/15.
 */
public class Code {
    private final String dictChars = "2345678abcdefgmnpwxy";
    public static final int WHITE = ((255 << 16) + (255 << 8) + 255);
    private final List<DictItem> dict = new ArrayList<DictItem>();

    public Code() throws IOException, InterruptedException {
        for (char c : dictChars.toCharArray()) {
            addDict(c);
        }
    }

    private void addDict(char c) throws IOException, InterruptedException {
        Img img = read(getClass().getResource("/dict/" + c + ".jpg"));
        dict.add(new DictItem(c, img));
        for (int i = 1; i < 5; i++) {
            img = read(getClass().getResource("/dict/" + c + i + ".jpg"));
            if (img == null) {
                break;
            } else {
                dict.add(new DictItem(c, img));
            }
        }
    }

    /**
     * http://www.guahao.com/validcode/genimage/7550034
     *
     * @param url
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public String guess(URL url) throws IOException, InterruptedException {
        StringBuilder sb = new StringBuilder();
        Img[] imgs = new Img[5];
        imgs[0] = toBw(read(url));
        int[] lefts = new int[4];
        for (int i = 0; i < 4; i++) {
            lefts[i] = imgs[i].calcLeft();
            DictItem dictItem = find(lefts[i], imgs[i]);
            sb.append(dictItem.c);
            imgs[i + 1] = erase(imgs[i], lefts[i], dictItem.img);
        }
//        Img img2 = addLine(imgs[2], lefts, new int[0]);
//        Img img2 = addLine(imgs[2], new int[]{77,110}, new int[0]);
//        save(img2, new File("/tmp/test.jpg"));
//        save(cut(imgs[0],76,110), new File("/tmp/test.jpg"));
        return sb.toString();
    }

    private static Img cut(Img img, int left, int right) {
        int newW = right - left;
        System.out.println(newW);
        System.out.println(img.height);
        int[] pixels = new int[newW * img.height];
        for (int i = left; i < right; i++) {
            for (int j = 0; j < img.height; j++) {
                int pixel = img.pixels[j * img.width + i];
                try {
                    pixels[j * newW + i - left] = pixel;
                } catch (Exception e) {
                    System.out.println(j);
                    System.out.println(newW);
                    System.out.println(newW);
                    return null;
                }
            }
        }
        return new Img(pixels, newW, img.height);
    }

    public static Img addLine(Img img, int[] vs, int[] hs) {
        int[] pixels = new int[img.pixels.length];
        for (int i = 0; i < img.width; i++) {
            for (int j = 0; j < img.height; j++) {
                int pixel = img.pixels[j * img.width + i];
                pixels[j * img.width + i] = pixel;
            }
        }
        for (int h : hs) {
            for (int i = 0; i < img.width; i++) {
                pixels[h * img.width + i] = WHITE;
            }
        }
        for (int v : vs) {
            for (int j = 0; j < img.height; j++) {
                pixels[j * img.width + v] = WHITE;
            }
        }
        return new Img(pixels, img.width, img.height);
    }

    public static void save(Img img, File file) throws IOException {
        DirectColorModel cm = new DirectColorModel(24, 0xff0000, 0xff00, 0xff);
        WritableRaster wr = cm.createCompatibleWritableRaster(1, 1);
        SampleModel sampleModel = wr.getSampleModel();
        sampleModel = sampleModel.createCompatibleSampleModel(img.width, img.height);

        DataBuffer dataBuffer = new DataBufferInt(img.pixels, img.width * img.height, 0);
        WritableRaster rgbRaster = Raster.createWritableRaster(sampleModel, dataBuffer, null);
        BufferedImage image = new BufferedImage(cm, rgbRaster, false, null);
        ImageIO.write(image, "jpg", file);
    }

    private static Img toBw(Img img) {
        int[] pixels = new int[img.pixels.length];
        for (int i = 0; i < img.height; i++) {
            for (int j = 0; j < img.width; j++) {
                int pixel = img.pixels[i * img.width + j];
                float[] hsb = Color.RGBtoHSB((pixel >> 16) & 0xff, (pixel >> 8) & 0xff, (pixel) & 0xff, null);
                if (hsb[2] > 0.91) {
                    pixel = 0;
                    hsb[2] = 1;
                } else {
                    pixel = WHITE;
                    hsb[2] = 0;
                }
                pixels[i * img.width + j] = pixel;
            }
        }
        return new Img(pixels, img.width, img.height);
    }

    private static Img erase(Img img, int left, Img erase) {
        int[] pixels = new int[img.pixels.length];
        for (int i = 0; i < img.width; i++) {
            for (int j = 0; j < img.height; j++) {
                int pixel = img.pixels[j * img.width + i];
                pixels[j * img.width + i] = pixel;
                if (i >= left && i < left + erase.width) {
                    if (inErase(erase, j, i - left)) {
                        pixels[j * img.width + i] = 0;
                    }
                }
            }

        }
        return new Img(pixels, img.width, img.height);
    }

    private static boolean inErase(Img erase, int j, int i) {
        if (readBright(erase.pixels[j * erase.width + i]) > 0.5) {
            return true;
        } else if (i < erase.width - 1 && readBright(erase.pixels[j * erase.width + i + 1]) > 0.5) {
            return true;
        } else if (i > 1 && readBright(erase.pixels[j * erase.width + i - 1]) > 0.5) {
            return true;
        } else if (j < erase.height - 1 && readBright(erase.pixels[(j + 1) * erase.width + i]) > 0.5) {
            return true;
        } else if (j > 1 && readBright(erase.pixels[(j - 1) * erase.width + i]) > 0.5) {
            return true;
        }
        return false;
    }

    private DictItem find(int left, Img img) {
        List<Score> scores = new ArrayList();
        for (DictItem dictItem : dict) {
            int diff = getDiff(left, img, dictItem);
            diff = Math.min(getDiff(left - 1, img, dictItem), diff);
            if (dictItem.img.width > 35) {
                diff = Math.min(getDiff(left - 2, img, dictItem), diff);
            }
            if (dictItem.img.width > 47) {
                diff = Math.min(getDiff(left - 3, img, dictItem), diff);
            }
//            if (dictItem.c == 'm') {
//                diff = diff - 288;
//            } else if (dictItem.c == 'w') {
//                diff = diff - 20;
//            } else {
//                int avg = diff/dictItem.img.width;
//            (48-dictItem.img.width)*avg = 48*diff/dictItem.img.width - diff;
            diff = (int) (1000 * diff / dictItem.img.width) - dictItem.img.width * 75;
//            }
            scores.add(new Score(dictItem, diff));
        }
        Collections.sort(scores, new Comparator<Score>() {
            @Override
            public int compare(Score o1, Score o2) {
                return o1.diff - o2.diff;
            }
        });
        char c0 = scores.get(0).dictItem.c;
        char c1 = scores.get(1).dictItem.c;
        if (c0 == 'm' && c1 != 'n') {
            return scores.get(1).dictItem;
        }
//        if (c0 == 'w' && c1 != 'v') {
//            return scores.get(1).dictItem;
//        }
        return scores.get(0).dictItem;
    }

    private int getDiff(int left, Img img, DictItem dictItem) {
        int diff = 0;
        for (int i = 0; i < dictItem.img.width; i++) {
            for (int j = 0; j < dictItem.img.height; j++) {
                float b1 = readBright(dictItem.img.pixels[j * dictItem.img.width + i]);
                float b2 = 0;
                try {
                    b2 = readBright(img.pixels[j * img.width + i + left]);
                } catch (ArrayIndexOutOfBoundsException e) {
                    diff++;
                }
                if (Math.abs(b1 - b2) > 0.5) {
                    diff++;
                }
            }
        }
        return diff;
    }

    private static class Score {
        private DictItem dictItem;
        private int diff;

        public Score(DictItem dictItem, int diff) {

            this.dictItem = dictItem;
            this.diff = diff;
        }
    }

    private static float readBright(int pixel) {
        float[] hsb = Color.RGBtoHSB((pixel >> 16) & 0xff, (pixel >> 8) & 0xff, (pixel) & 0xff, null);
        return hsb[2];
    }

    public static Img read(URL url) throws IOException, InterruptedException {
        if (url == null) return null;
        BufferedImage img = ImageIO.read(url);
        int width = img.getWidth();
        int height = img.getHeight();
        int[] pixels = new int[width * height];
        PixelGrabber pixelGrabber = new PixelGrabber(img, 0, 0, width, height, pixels, 0, width);
        pixelGrabber.grabPixels();
        return new Img(pixels, width, height);
    }

    private static class DictItem {
        private Img img;
        private char c;

        public DictItem(char c, Img img) {
            this.c = c;
            this.img = img;
        }
    }

    private static class Img {
        public final int[] pixels;
        public final int width;
        public final int height;

        public Img(int[] pixels, int width, int height) {

            this.pixels = pixels;
            this.width = width;
            this.height = height;
        }

        public int calcLeft() {
            int left = 0;
            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    if (left < width - 1 && j < height - 1) {
                        if (left == 0) {
                            if (isWhite(pixels[j * width + i])
                                    && isWhite(pixels[j * width + i + 1])//right pixel
                                    && isWhite(pixels[(j + 1) * width + i])//under pixel
                                    && isWhite(pixels[(j + 1) * width + i + 1])//right under pixel
                                    ) {
                                if (isManyWhite(i, i + 1, i + 2, i + 3, i + 4, i + 5, i + 6, i + 7, i + 8, i + 9, i + 10, i + 11, i + 12)) {
                                    return i;
                                }
                            }
                        }
                    }
                }
            }
            return left;
        }

        private boolean isManyWhite(int... columns) {
            for (int i : columns) {
                int sum = 0;
                for (int j = 0; j < height; j++) {
                    if (isWhite(pixels[j * width + i])) {
                        sum++;
                    }
                }
                if (sum < 5) return false;
            }
            return true;
        }

        private boolean isWhite(int pixel) {
            float[] hsb = Color.RGBtoHSB((pixel >> 16) & 0xff, (pixel >> 8) & 0xff, (pixel) & 0xff, null);
            return hsb[2] > 0.5;
        }
    }
}

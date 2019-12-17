package com.march.gifmaker.extend;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.march.gifmaker.base.GifEncoder;
import com.march.gifmaker.base.LZWEncoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;


/**
 * CreateAt : 7/12/17
 * Describe :
 * 扩展 GifEncoder， 为多线程处理任务
 *
 * @author chendong
 */
public class ThreadGifEncoder extends GifEncoder {

    public LZWEncoderOrderHolder addFrame(Bitmap im, int order) {

        if (im == null || !started) {
            return null;
        }

        boolean ok = true;
        LZWEncoder lzwEncoder = null;
        try {
//                MyLog.i("---压缩帧前:w="+im.getWidth()+",h="+im.getHeight()+",size="+im.getByteCount());
            try{
//                   Bitmap temp = Bitmap.createScaledBitmap(im, (int) (im.getWidth()*0.6f), (int) (im.getHeight()*0.6f), true);

                byte[] bytes = byteArrayFromBitmap(im,100);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_4444;
                options.inSampleSize = 2;
                Bitmap temp = BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if (temp != null){
                    im.recycle();
                    im = temp;
//                       MyLog.i("---压缩帧后:w="+im.getWidth()+",h="+im.getHeight()+",size="+im.getByteCount());
                }
            }catch (OutOfMemoryError e){
                e.printStackTrace();
            }
            image = im;
            if (!sizeSet) {
                // 使用第一张图片的大小作为gif的大小
                setSize(im.getWidth(), im.getHeight());
            }
            getImagePixels(); // convert to correct format if necessary
            analyzePixels(); // build color table & map pixels
            if (firstFrame) {
                writeLSD(); // logical screen descriptior
                writePalette(); // global color table
                if (repeat >= 0) {
                    // use NS app extension to indicate reps
                    writeNetscapeExt();
                }
            }
            writeGraphicCtrlExt(); // write graphic control extension
            writeImageDesc(); // image descriptor
            if (!firstFrame) {
                writePalette(); // local color table
            }
            // writePixels(); // encode and write pixel data
            lzwEncoder = waitWritePixels();
        } catch (IOException e) {
            ok = false;
        }
        if (ok) {
            return new LZWEncoderOrderHolder(lzwEncoder, order);
        } else {
            return null;
        }
    }

    public byte[] byteArrayFromBitmap(Bitmap bitmap, int percent){
        byte[] data = null;
        ByteArrayOutputStream baos = null;
        try {
            baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, percent, baos);
            data = baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if(baos != null) {
                    baos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return data;
    }
    public void setSize(int w, int h) {
        width = w;
        height = h;
        if (width < 1)
            width = 200;
        if (height < 1)
            height = 200;
        sizeSet = true;
    }


    public void setFirstFrame(boolean firstFrame) {
        this.firstFrame = firstFrame;
    }


    public boolean start(OutputStream os, int num) {
        if (os == null)
            return false;
        boolean ok = true;
        closeStream = false;
        out = os;
        if (num == 0) { // 第一个
            try {
                writeString("GIF89a"); // header
            } catch (IOException e) {
                e.printStackTrace();
                ok = false;
            }
        }
        return started = ok;
    }


    private LZWEncoder waitWritePixels() throws IOException {
        return new LZWEncoder(width, height, indexedPixels, colorDepth);
    }


    public boolean finishThread(boolean isLast, LZWEncoder lzwEncoder) {
        if (!started)
            return false;
        boolean ok = true;
        started = false;

        try {
            lzwEncoder.encode(out);
            if (isLast) {
                out.write(0x3b); // gif trailer
            }
            out.flush();
            if (closeStream) {
                out.close();
            }
        } catch (IOException e) {
            ok = false;
        }

        // reset for subsequent use
        transIndex = 0;
        out = null;
        image = null;
        pixels = null;
        indexedPixels = null;
        colorTab = null;
        closeStream = false;
        firstFrame = true;
        return ok;
    }
}

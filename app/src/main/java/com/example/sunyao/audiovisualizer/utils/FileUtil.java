package com.example.sunyao.audiovisualizer.utils;

import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import static android.content.ContentValues.TAG;

/**
 * @author sunyao
 * @Description:
 * @date 2018/1/18 下午4:54
 */
public class FileUtil {
    public static File getDir() {
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/AudioVisualizer";
        File file = new File(path);
        if (!file.exists()) {
            file.mkdirs();
        }

        return file;
    }

    public static String getPath(String name) {
        return getDir().getAbsolutePath() + "/" + name;
    }

    public static File getFile(String name) {
        File file = new File(getPath(name));
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return file;
    }

    public static void convertAudioFiles(File src, File target) throws Exception {
        FileInputStream fis = new FileInputStream(src);
        FileOutputStream fos = new FileOutputStream(target);


        byte[] buf = new byte[1024 * 1000];
        int size = fis.read(buf);
        int PCMSize = 0;
        while (size != -1) {
            PCMSize += size;
            size = fis.read(buf);
        }
        fis.close();


        WaveHeader header = new WaveHeader();
        header.fileLength = PCMSize + (44 - 8);
        header.FmtHdrLeth = 16;
        header.BitsPerSample = 16;
        header.Channels = 1;
        header.FormatTag = 0x0001;
        header.SamplesPerSec = 44100;
        header.BlockAlign = (short) (header.Channels * header.BitsPerSample / 8);
        header.AvgBytesPerSec = header.BlockAlign * header.SamplesPerSec;
        header.DataHdrLeth = PCMSize;

        byte[] h = header.getHeader();

        assert h.length == 44;
        //write header
        fos.write(h, 0, h.length);
        //write data stream
        fis = new FileInputStream(src);
        size = fis.read(buf);
        while (size != -1) {
            fos.write(buf, 0, size);
            size = fis.read(buf);
        }
        fis.close();
        fos.close();
//        System.out.println(System.currentTimeMillis()+"wav");
//        System.out.println("Convert OK!");
    }
}

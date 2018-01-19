package com.example.sunyao.audiovisualizer.utils;

/**
 * @author sunyao
 * @Description:
 * @date 2018/1/18 下午3:05
 */
public class AudioDataUtil {
    /**
     * 归一化
     */
    public static double[] normalize(short[] data) {
        short max = findMax(data);
        max = 32767;
        double[] result = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = ((double) data[i] / max);
//            if (result[i] < 0.005 && result[i] > -0.005) {
//                result[i] = 0;
//            }

        }
        return result;
    }

    public static short[] lowerPb(short[] data) {
        for (int i = 0; i < data.length; i++) {
            data[i] = (short) (data[i] / 4);
        }
        return data;
    }


    /**
     * 查找最大值
     *
     * @param data
     * @return
     */
    private static short findMax(short[] data) {
        short max = data[0];
        for (int i = 0; i < data.length; i++) {
            if (max < Math.abs(data[i])) {
                max = (short) Math.abs(data[i]);
            }
        }
        System.out.println("max :  " + max);
        return max;
    }

}

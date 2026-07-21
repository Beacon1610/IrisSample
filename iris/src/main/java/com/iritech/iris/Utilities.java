package com.iritech.iris;

public class Utilities {
    private static final int HEADER_SIZE = 14;
    private static final int INFO_SIZE = 40;
    private static final int COLOR_TBL_ROW = 256;
    private static final int COLOR_TBL_SIZE = 256 * 4;

    public static byte[] convertRawImageToBitmap(byte[] rawImage, int imageWidth, int imageHeight)
    {
        // Convert to 8bit bmp grayScale
        int allHeaderSize = HEADER_SIZE + INFO_SIZE + COLOR_TBL_SIZE;

        //int padding = getPadding(width);
        int fileSize = allHeaderSize + (imageWidth * imageHeight);
        byte[] arrGrayScale = new byte[fileSize];

        // Index to current position of bmpGrayScale
        int[] index = new int[1];
        index[0] = 0;

        // Make header
        int temp4Bytes;
        short temp2Bytes;
        byte temp1Bytes;

        temp1Bytes = 0x42;
        set1ByteData(arrGrayScale, index, temp1Bytes); // B

        temp1Bytes = 0x4D;
        set1ByteData(arrGrayScale, index, temp1Bytes); // M

        temp4Bytes = fileSize;
        set4ByteData(arrGrayScale, index, temp4Bytes);  // 4 bytes of file size

        temp1Bytes = 0x00;
        set1ByteData(arrGrayScale, index, temp1Bytes); //
        set1ByteData(arrGrayScale, index, temp1Bytes); //
        set1ByteData(arrGrayScale, index, temp1Bytes); //
        set1ByteData(arrGrayScale, index, temp1Bytes); //

        temp4Bytes = HEADER_SIZE + INFO_SIZE + COLOR_TBL_SIZE; // headerSize + infoSize + colorInfoSize
        set4ByteData(arrGrayScale, index, temp4Bytes);  // 4 bytes of pixel info offset

        temp4Bytes = INFO_SIZE;
        set4ByteData(arrGrayScale, index, temp4Bytes);  // 4 bytes of size of info header

        temp4Bytes = imageWidth;
        set4ByteData(arrGrayScale, index, temp4Bytes); // 4 bytes of width

        temp4Bytes = imageHeight;
        set4ByteData(arrGrayScale, index, temp4Bytes); // 4 bytes of height

        temp2Bytes = 1;
        set2ByteData(arrGrayScale, index, temp2Bytes); // 2 bytes of number color planes

        temp2Bytes = 8;
        set2ByteData(arrGrayScale, index, temp2Bytes); // 2 bytes of bits per pixel

        temp4Bytes = 0;
        set4ByteData(arrGrayScale, index, temp4Bytes);  // 4 bytes of compression

        temp4Bytes = fileSize - (HEADER_SIZE + INFO_SIZE + COLOR_TBL_SIZE);
        set4ByteData(arrGrayScale, index, temp4Bytes);  // 4 bytes of size of pixel data = fileSize - totalHeaderSize

        temp4Bytes = 2835;
        set4ByteData(arrGrayScale, index, temp4Bytes);  // 4 bytes of Horizontal Resolution

        temp4Bytes = 2835;
        set4ByteData(arrGrayScale, index, temp4Bytes);   // 4 bytes of Vertical Resolution

        temp4Bytes = 0;
        set4ByteData(arrGrayScale, index, temp4Bytes);  // 4 bytes of Number of colors

        temp4Bytes = 0;
        set4ByteData(arrGrayScale, index, temp4Bytes);   // 4 bytes of Number of colors

        // Color table
        for (int i = 0; i < COLOR_TBL_ROW; ++i) {
            arrGrayScale[index[0]++] = (byte) i;
            arrGrayScale[index[0]++] = (byte) i;
            arrGrayScale[index[0]++] = (byte) i;
            arrGrayScale[index[0]++] = (byte) 0;
        }

        // Reverse image
        for (int i = imageHeight - 1; i >= 0; --i) {
            for (int j = 0; j < imageWidth; ++j) {
                arrGrayScale[index[0]++] = rawImage[i * imageWidth + j];
            }
        }
        return arrGrayScale;
    }
    private static void set1ByteData(byte[] bmp, int[] index, byte value){
        bmp[index[0]++] = value;
    }

    private static void set2ByteData(byte[] bmp, int[] index, short value){
        bmp[index[0]++] = (byte)(value & 0x00FF);
        bmp[index[0]++] = (byte)((value >>  8) & 0x00FF);
    }

    private static void set4ByteData(byte[] bmp, int[] index, int value){
        bmp[index[0]++] = (byte) (value & 0x00FF);
        bmp[index[0]++] = (byte) ((value >>  8) & 0x000000FF);
        bmp[index[0]++] = (byte) ((value >>  16) & 0x000000FF);
        bmp[index[0]++] = (byte) ((value >>  24) & 0x000000FF);
    }
}

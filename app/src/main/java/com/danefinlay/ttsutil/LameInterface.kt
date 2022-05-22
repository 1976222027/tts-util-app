package com.danefinlay.ttsutil

/**
 * This class is a wrapper around the LAME library functions.
 */
class LameInterface {
    private var gfpPtr: Long

    init {
        // Load the native library and initialize the encoder.
        System.loadLibrary("libmp3lame")

        // Initialize and store a pointer to the LAME encoder.
        gfpPtr = lameInit()
    }

    /*
     Initialize and finalize functions.
     */
    private external fun lameInit(): Long
    private external fun lameClose(ptr: Long): Int

    fun free() = lameClose(gfpPtr)

    /*
     Encode
     */

    /*
     Encoder parameter functions.
     */
    private external fun setParam(ptr: Long, type: Int, value: Int): Int
    private external fun initParams(ptr: Long): Int
    private fun setParam(type: Int, value: Int): Int = setParam(gfpPtr, type, value)
    fun initParams(): Int = initParams(gfpPtr)
    fun setNumSamples(n: Int): Int            = setParam(0, n)
    fun setInSampleRate(rate: Int): Int       = setParam(1, rate)
    fun setNumChannels(n: Int): Int           = setParam(2, n)
    fun setOutSampleRate(rate: Int): Int      = setParam(3, rate)
    fun setQuality(quality: Int): Int         = setParam(4, quality)
    fun setCompressionRatio(ratio: Int): Int  = setParam(5, ratio)

    /*
     Encoding functions.
     */
    private external fun encodeBuffer(ptr: Long,
                                      leftPcm: IntArray,
                                      rightPcm: IntArray,
                                      mp3Buffer: CharArray): Int
    fun encodeBuffer(leftPcm: IntArray, rightPcm: IntArray,
                     mp3Buffer: CharArray): Int =
            encodeBuffer(gfpPtr, leftPcm, rightPcm, mp3Buffer)
}

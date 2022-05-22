/*
 * TTS Util
 *
 * Authors: Dane Finlay <Danesprite@posteo.net>
 *
 * Copyright (C) 2022 Dane Finlay
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <jni.h>
#include "libmp3lame/lame.h"

extern "C"
JNIEXPORT jlong JNICALL
Java_com_danefinlay_ttsutil_LameInterface_lameInit(JNIEnv *env,
                                                   jobject thiz) {
    lame_global_flags *gfp = lame_init();
    return reinterpret_cast<long>(gfp);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_danefinlay_ttsutil_LameInterface_setParam(JNIEnv *env,
                                          jobject thiz,
                                          jlong ptr,
                                          jint type,
                                          jint value) {
    auto *gfp = reinterpret_cast<lame_global_flags *>(ptr);
    int res;
    switch (type) {
        case 0: res = lame_set_num_samples(gfp, value); break;
        case 1: res = lame_set_in_samplerate(gfp, value); break;
        case 2: res = lame_set_num_channels(gfp, value); break;
        case 3: res = lame_set_out_samplerate(gfp, value); break;
        case 4: res = lame_set_quality(gfp, value); break;
        case 5: res = lame_set_compression_ratio(gfp, value); break;
        default: res = -1;
    }
    return res;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_danefinlay_ttsutil_LameInterface_initParams(JNIEnv *env,
                                                     jobject thiz,
                                                     jlong ptr) {
    auto *gfp = reinterpret_cast<lame_global_flags *>(ptr);
    return lame_init_params(gfp);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_danefinlay_ttsutil_LameInterface_lameClose(JNIEnv *env,
                                                    jobject thiz,
                                                    jlong ptr) {
    auto *gfp = reinterpret_cast<lame_global_flags *>(ptr);
    return lame_close(gfp);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_danefinlay_ttsutil_LameInterface_encodeBuffer(JNIEnv *env, jobject thiz,
                                                       jlong ptr,
                                                       jintArray left_pcm,
                                                       jintArray right_pcm,
                                                       jcharArray mp3_buffer) {
    // TODO: implement encodeBuffer()
    // Get the encoder and number of samples.
    auto *gfp = reinterpret_cast<lame_global_flags *>(ptr);
    int num_samples = (int)lame_get_num_samples(gfp);

    // Convert left_pcm array.
    int left_data_length = env->GetArrayLength(left_pcm);
    int buffer_l[left_data_length];
    jint* array_l = env->GetIntArrayElements(left_pcm, nullptr);
    for (int i = 0; i < left_data_length; ++i) {
        buffer_l[i] = (int)array_l[i];
    }

    // Convert right_pcm array.
    // Note: Right is normally empty.  This is OK if num_channels=1.
    int right_data_length = env->GetArrayLength(right_pcm);
    int buffer_r[right_data_length];
    jint* array_r = env->GetIntArrayElements(right_pcm, nullptr);
    for (int i = 0; i < right_data_length; ++i) {
        buffer_r[i] = (int)array_r[i];
    }

    // Initialize the MP3 output buffer.
    int mp3_buffer_size = env->GetArrayLength(mp3_buffer);
    unsigned char mp3_buffer_c[mp3_buffer_size];

    // Disable writing of the VBR tag.
    lame_set_bWriteVbrTag(gfp, 0);

    // Encode the PCM audio and flush the buffers.
    int res = lame_encode_buffer_int(gfp, buffer_l, buffer_r, num_samples,
                                     mp3_buffer_c, mp3_buffer_size);
    if (res >= 0) {
        int flush_res = lame_encode_flush(gfp, mp3_buffer_c, mp3_buffer_size);
        if (flush_res >= 0) res += flush_res;
        else res = flush_res;
    }

    // If successful, copy the audio into the output buffer.
    if (res >= 0) {
        lame_mp3_tags_fid(gfp, nullptr);
        env->SetCharArrayRegion(mp3_buffer, 0, res,
                                reinterpret_cast<const jchar *>(mp3_buffer_c));
    }

    // Return the final result: error code or number of bytes output to the buffer.
    return res;
}

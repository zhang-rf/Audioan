#include <jni.h>
#include <unistd.h>
#include <assert.h>
#include <pthread.h>
#include <semaphore.h>

// for native audio
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

// for __android_log_print(ANDROID_LOG_INFO, "YourApp", "formatted message");
//#include <android/log.h>

#define BUFFER_NUMBER 100

struct BufferLinker {
    jbyte *buffer;
    BufferLinker *next;
    BufferLinker *last;
};

// engine interfaces
static SLObjectItf engineObject = NULL;
static SLEngineItf engineEngine;

// output mix interfaces
static SLObjectItf outputMixObject = NULL;

// buffer queue player interfaces
static SLObjectItf bqPlayerObject = NULL;
static SLPlayItf bqPlayerPlay;
static SLAndroidSimpleBufferQueueItf bqPlayerBufferQueue;

static BufferLinker *bufferLinker = NULL;
static volatile int lockFailCount = 0;
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
static sem_t sem;

// called automatically on load
__attribute__((constructor)) static void constructor(void) {
    sem_init(&sem, 0, BUFFER_NUMBER);
}

// called automatically on unload
__attribute__((destructor)) static void destructor(void) {
    sem_destroy(&sem);
}

// this callback handler is called every time a buffer finishes playing
void bqPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context) {
    assert(bq == bqPlayerBufferQueue);
    assert(NULL == context);

    bool locked = pthread_mutex_trylock(&mutex) == 0;
    if (!locked)
        lockFailCount++;
    else {
        for (int i = 0; i <= lockFailCount; i++) {
            if (bufferLinker != NULL) {
                BufferLinker *next = bufferLinker->next;
                BufferLinker *last = bufferLinker->last;
                delete[] bufferLinker->buffer;
                delete bufferLinker;
                bufferLinker = next;
                if (bufferLinker != NULL)
                    bufferLinker->last = last;
                sem_post(&sem);
            }
        }
        lockFailCount = 0;
        pthread_mutex_unlock(&mutex);
    }
}

extern "C" {

JNIEXPORT void JNICALL Java_com_audioan_audioan_jni_NativeAudio_createEngine
        (JNIEnv *env, jclass clazz) {
    SLresult result;

    // create engine
    result = slCreateEngine(&engineObject, 0, NULL, 0, NULL, NULL);
    assert(SL_RESULT_SUCCESS == result);
    (void) result;

    // realize the engine
    result = (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
    assert(SL_RESULT_SUCCESS == result);
    (void) result;

    // get the engine interface, which is needed in order to create other objects
    result = (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineEngine);
    assert(SL_RESULT_SUCCESS == result);
    (void) result;

    // create output mix
    result = (*engineEngine)->CreateOutputMix(engineEngine, &outputMixObject, 0, NULL, NULL);
    assert(SL_RESULT_SUCCESS == result);
    (void) result;

    // realize the output mix
    result = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);
    assert(SL_RESULT_SUCCESS == result);
    (void) result;
}

JNIEXPORT void JNICALL Java_com_audioan_audioan_jni_NativeAudio_createBufferQueueAudioPlayer
        (JNIEnv *env, jclass clazz, jint sampleRateInHz) {
    SLresult result;

    // configure audio source
    SLDataLocator_AndroidSimpleBufferQueue loc_bufq = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,
                                                       BUFFER_NUMBER};
    SLDataFormat_PCM format_pcm = {SL_DATAFORMAT_PCM, 2, (SLuint32) (sampleRateInHz * 1000),
                                   SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
                                   SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT,
                                   SL_BYTEORDER_LITTLEENDIAN};
    SLDataSource audioSrc = {&loc_bufq, &format_pcm};

    // configure audio sink
    SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject};
    SLDataSink audioSnk = {&loc_outmix, NULL};

    // create audio player
    const SLInterfaceID ids[1] = {SL_IID_BUFFERQUEUE};
    const SLboolean req[1] = {SL_BOOLEAN_TRUE};
    result = (*engineEngine)->CreateAudioPlayer(engineEngine, &bqPlayerObject, &audioSrc, &audioSnk,
                                                1, ids, req);
    assert(SL_RESULT_SUCCESS == result);
    (void) result;

    // realize the player
    result = (*bqPlayerObject)->Realize(bqPlayerObject, SL_BOOLEAN_FALSE);
    assert(SL_RESULT_SUCCESS == result);
    (void) result;

    // get the play interface
    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_PLAY, &bqPlayerPlay);
    assert(SL_RESULT_SUCCESS == result);
    (void) result;

    // get the buffer queue interface
    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_BUFFERQUEUE,
                                             &bqPlayerBufferQueue);
    assert(SL_RESULT_SUCCESS == result);
    (void) result;

    // register callback on the buffer queue
    result = (*bqPlayerBufferQueue)->RegisterCallback(bqPlayerBufferQueue, bqPlayerCallback, NULL);
    assert(SL_RESULT_SUCCESS == result);
    (void) result;
}

JNIEXPORT void JNICALL Java_com_audioan_audioan_jni_NativeAudio_write
        (JNIEnv *env, jclass clazz, jbyteArray audioData, jint offset, jint size) {
    SLresult result;

    sem_wait(&sem);
    pthread_mutex_lock(&mutex);
    BufferLinker *newBufferLinker = new BufferLinker;
    newBufferLinker->buffer = new jbyte[size];
    newBufferLinker->next = NULL;
    env->GetByteArrayRegion(audioData, offset, size, newBufferLinker->buffer);
    if (bufferLinker == NULL) {
        bufferLinker = newBufferLinker;
        bufferLinker->last = newBufferLinker;
    }
    else {
        bufferLinker->last->next = newBufferLinker;
        bufferLinker->last = newBufferLinker;
    }
    pthread_mutex_unlock(&mutex);

    result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, newBufferLinker->buffer,
                                             (SLuint32) size);
    // the most likely other result is SL_RESULT_BUFFER_INSUFFICIENT,
    // which for this code example would indicate a programming error
    assert(SL_RESULT_SUCCESS == result);
    (void) result;
}

JNIEXPORT void JNICALL Java_com_audioan_audioan_jni_NativeAudio_play
        (JNIEnv *env, jclass clazz) {
    // set the player's state to playing
    SLresult result;
    result = (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING);
    assert(SL_RESULT_SUCCESS == result);
    (void) result;
}

JNIEXPORT void JNICALL Java_com_audioan_audioan_jni_NativeAudio_stop
        (JNIEnv *env, jclass clazz) {
    // set the player's state to stopped
    SLresult result;
    result = (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_STOPPED);
    assert(SL_RESULT_SUCCESS == result);
    (void) result;

    result = (*bqPlayerBufferQueue)->Clear(bqPlayerBufferQueue);
    assert(SL_RESULT_SUCCESS == result);
    (void) result;

    pthread_mutex_lock(&mutex);
    while (bufferLinker != NULL) {
        BufferLinker *next = bufferLinker->next;
        delete[] bufferLinker->buffer;
        delete bufferLinker;
        bufferLinker = next;
        sem_post(&sem);
    }
    lockFailCount = 0;
    pthread_mutex_unlock(&mutex);
}

JNIEXPORT jint JNICALL Java_com_audioan_audioan_jni_NativeAudio_getBufferPadding
        (JNIEnv *env, jclass clazz) {
    int value;
    sem_getvalue(&sem, &value);
    return BUFFER_NUMBER - value;
}

JNIEXPORT void JNICALL Java_com_audioan_audioan_jni_NativeAudio_shutdown
        (JNIEnv *env, jclass clazz) {
    // destroy buffer queue audio player object, and invalidate all associated interfaces
    if (bqPlayerObject != NULL) {
        (*bqPlayerObject)->Destroy(bqPlayerObject);
        bqPlayerObject = NULL;
        bqPlayerPlay = NULL;
        bqPlayerBufferQueue = NULL;
    }

    // destroy output mix object, and invalidate all associated interfaces
    if (outputMixObject != NULL) {
        (*outputMixObject)->Destroy(outputMixObject);
        outputMixObject = NULL;
    }

    // destroy engine object, and invalidate all associated interfaces
    if (engineObject != NULL) {
        (*engineObject)->Destroy(engineObject);
        engineObject = NULL;
        engineEngine = NULL;
    }
}

}

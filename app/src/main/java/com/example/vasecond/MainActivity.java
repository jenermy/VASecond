package com.example.vasecond;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Path;

public class MainActivity extends AppCompatActivity {
    private Button changeToneBtn;
    private Button extractorMedia;
    private Button muxMedia;
    private long TIMEOUT_USEC = 10000;
    private static final String OUTPUT_VIDEO_MIME_TYPE = "video/avc";
    private static final String OUTPUT_AUDIO_MIME_TYPE = "audio/mp4a-latm";
    private static final int OUTPUT_VIDEO_BIT_RATE = 512 * 1024; // 512 kbps maybe better
    private static final int OUTPUT_VIDEO_FRAME_RATE = 25; // 25fps
    private static final int OUTPUT_VIDEO_IFRAME_INTERVAL = 10; // 10 seconds between I-frames
    private static final int OUTPUT_VIDEO_COLOR_FORMAT = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
    private static final int OUTPUT_AUDIO_BIT_RATE = 64 * 1024; // 64 kbps
    private static final int OUTPUT_AUDIO_AAC_PROFILE = MediaCodecInfo.CodecProfileLevel.AACObjectLC; // better than AACObjectHE
    /** parameters for the audio encoder config from input stream */
    private int OUTPUT_AUDIO_CHANNEL_COUNT = 1; // Must match the input stream.can not config
    private int OUTPUT_AUDIO_SAMPLE_RATE_HZ = 48000; // Must match the input stream.can not config
    /** Whether to copy the video from the test video. */
    private boolean mCopyVideo = true;
    /** Whether to copy the audio from the test audio. */
    private boolean mCopyAudio = true;
    /** Width of the output frames. */
    private int mWidth = -1;
    /** Height of the output frames. */
    private int mHeight = -1;

    /** The raw resource used as the input file. */
    private String mBaseFileRoot;
    /** The raw resource used as the input file. */
    private String mBaseFile;
    /** The destination file for the encoded output. */
    private String mOutputFile;

    private boolean interrupted = false;
    private MediaExtractor mMediaExtractor;
    private MediaMuxer mMediaMuxer;
    private Button muxAudio;

    private String outputVedioPath = Environment.getExternalStorageDirectory() + File.separator + "va/mux.mp4";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        changeToneBtn = (Button)findViewById(R.id.changeToneBtn);
        changeToneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(ActivityCompat.checkSelfPermission(MainActivity.this,Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                    ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},101);
                }else{
                    try {
                        muxAudioAndVedio();
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
        });
        extractorMedia = (Button)findViewById(R.id.extractorMedia);
        extractorMedia.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //分离出来的视频和音频都播放不了
                extractorMedia();
            }
        });
        muxMedia = (Button)findViewById(R.id.muxMedia);
        muxMedia.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //分离出来的视频没有声音，但是可以播放
                muxMedia();
            }
        });
        muxAudio = (Button)findViewById(R.id.muxAudio);
        muxAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DecodeEncode decodeEncode = DecodeEncode.newInstance();
                decodeEncode.prepare();
                decodeEncode.startAsync();
                decodeEncode.release();
                //muxAudio();
            }
        });
    }
    private void muxAudio(){
        mMediaExtractor = new MediaExtractor();
        try {
            mMediaExtractor.setDataSource(Environment.getExternalStorageDirectory()+"/Video/autumn.mp4");
            int trackCount = mMediaExtractor.getTrackCount();
            int audioTrackIndex = -1;
            for(int i=0;i<trackCount;i++){
                String mime = mMediaExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if(mime.startsWith("audio/")){
                    audioTrackIndex = i;
                    break;
                }
            }
            mMediaExtractor.selectTrack(audioTrackIndex);
            MediaFormat mediaFormat = mMediaExtractor.getTrackFormat(audioTrackIndex);
            Log.i("wanlijun",mediaFormat.getString(MediaFormat.KEY_MIME));
            mMediaMuxer = new MediaMuxer(Environment.getExternalStorageDirectory()+"/va/output_audio", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int audioTrack = mMediaMuxer.addTrack(mediaFormat);
            mMediaMuxer.start();
            ByteBuffer byteBuffer = ByteBuffer.allocate(500 * 1024);
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            long sampleTime;
            mMediaExtractor.readSampleData(byteBuffer,0);
            if(mMediaExtractor.getSampleFlags() == MediaExtractor.SAMPLE_FLAG_SYNC)
                mMediaExtractor.advance();
            mMediaExtractor.readSampleData(byteBuffer,0);
            long firstSampleTime = mMediaExtractor.getSampleTime();
            mMediaExtractor.advance();
            mMediaExtractor.readSampleData(byteBuffer,0);
            long secondSampleTime = mMediaExtractor.getSampleTime();
            sampleTime = Math.abs(secondSampleTime - firstSampleTime);
            mMediaExtractor.unselectTrack(audioTrackIndex);
            mMediaExtractor.selectTrack(audioTrackIndex);
            while (true){
                int readSampleData = mMediaExtractor.readSampleData(byteBuffer,0);
                if(readSampleData < 0)break;
                mMediaExtractor.advance();
                bufferInfo.size = readSampleData;
                bufferInfo.offset = 0;
                bufferInfo.flags = mMediaExtractor.getSampleFlags();
                bufferInfo.presentationTimeUs += sampleTime;
                mMediaMuxer.writeSampleData(audioTrack,byteBuffer,bufferInfo);
            }
            mMediaMuxer.stop();
            mMediaMuxer.release();
            mMediaExtractor.release();
        }catch (Exception e){
            e.printStackTrace();
            Log.i("wanlijun",e.toString());
        }
    }

    private void muxMedia(){
        mMediaExtractor = new MediaExtractor();
        try {
            mMediaExtractor.setDataSource(Environment.getExternalStorageDirectory()+"/Video/autumn.mp4");
            int vedioTrackIndex = -1;
            int trackCount = mMediaExtractor.getTrackCount();
            for(int i= 0;i<trackCount;i++){
                String mime = mMediaExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if(mime.startsWith("video/")){
                    vedioTrackIndex = i;
                    break;
                }
            }
            mMediaExtractor.selectTrack(vedioTrackIndex);
            MediaFormat mediaFormat = mMediaExtractor.getTrackFormat(vedioTrackIndex);
            mMediaMuxer = new MediaMuxer(Environment.getExternalStorageDirectory()+"/va/output_mux",MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int trackIndex = mMediaMuxer.addTrack(mediaFormat);
            ByteBuffer byteBuffer = ByteBuffer.allocate(500 * 1024);
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            mMediaMuxer.start();
            mMediaExtractor.readSampleData(byteBuffer,0);
            if(mMediaExtractor.getSampleFlags() == MediaExtractor.SAMPLE_FLAG_SYNC)
                mMediaExtractor.advance();
            mMediaExtractor.readSampleData(byteBuffer,0);
            long firstVideoPTS = mMediaExtractor.getSampleTime();
            mMediaExtractor.advance();
            mMediaExtractor.readSampleData(byteBuffer,0);
            long secondVideoPTS = mMediaExtractor.getSampleTime();
            long videoSampleTiem = Math.abs(secondVideoPTS - firstVideoPTS);
            mMediaExtractor.unselectTrack(vedioTrackIndex);
            mMediaExtractor.selectTrack(vedioTrackIndex);
            while (true){
                int readSampleCount = mMediaExtractor.readSampleData(byteBuffer,0);
                if(readSampleCount < 0) break;
                mMediaExtractor.advance();
                bufferInfo.size = readSampleCount;
                bufferInfo.presentationTimeUs += videoSampleTiem;
                bufferInfo.flags = mMediaExtractor.getSampleFlags();
                bufferInfo.offset = 0;
                mMediaMuxer.writeSampleData(trackIndex,byteBuffer,bufferInfo);
            }
            if(mMediaMuxer != null){
                mMediaMuxer.stop();
                mMediaMuxer.release();
            }
            if(mMediaExtractor != null){
                mMediaExtractor.release();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    //分离音轨和视轨
    private void extractorMedia(){
        mMediaExtractor = new MediaExtractor();
        FileOutputStream videoOutputStream = null;
        FileOutputStream audioOutputStream = null;
        try {
            File videoFile = new File(outputVedioPath);
            if(!videoFile.exists()) videoFile.createNewFile();
            File audioFile = new File(Environment.getExternalStorageDirectory()+File.separator+"va/mux");
            videoOutputStream = new FileOutputStream(videoFile);
            audioOutputStream = new FileOutputStream(audioFile);
            mMediaExtractor.setDataSource(Environment.getExternalStorageDirectory()+"/Video/autumn.mp4");
            int trackCount = mMediaExtractor.getTrackCount();
            int videoTrackIndex = -1;
            int audioTrackIndex = -1;
            for(int i=0;i<trackCount;i++){
                MediaFormat mediaFormat = mMediaExtractor.getTrackFormat(i);
                String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
                if(mime.startsWith("video/")){
                    videoTrackIndex = i;
                }
                if(mime.startsWith("audio/")){
                    audioTrackIndex = i;
                }
            }
            ByteBuffer byteBuffer = ByteBuffer.allocate(500 * 1024);
            mMediaExtractor.selectTrack(videoTrackIndex);
            while (true){
                int readSampleCount = mMediaExtractor.readSampleData(byteBuffer,0);
                if(readSampleCount < 0)break;
                byte[] buffer = new byte[readSampleCount];
                byteBuffer.get(buffer);
                videoOutputStream.write(buffer);
                byteBuffer.clear();
                mMediaExtractor.advance();
            }
            mMediaExtractor.selectTrack(audioTrackIndex);
            while (true){
                int readSampleCount = mMediaExtractor.readSampleData(byteBuffer,0);
                if(readSampleCount < 0)break;
                byte[] buffer = new byte[readSampleCount];
                byteBuffer.get(buffer);
                audioOutputStream.write(buffer);
                byteBuffer.clear();
                mMediaExtractor.advance();
            }

        }catch (Exception e){
            e.printStackTrace();
            Log.i("wanlijun",e.toString());
        }finally {
            mMediaExtractor.release();
            try {
                videoOutputStream.close();
            }catch (Exception e){
                e.printStackTrace();
                Log.i("wanlijun",e.toString());
            }
            try {
                audioOutputStream.close();
            }catch (Exception e){
                e.printStackTrace();
                Log.i("wanlijun",e.toString());
            }
        }

    }

    private void changeCodedFormat(){

    }

    private void muxAudioAndVedio() throws Exception{
        MediaMuxer mediaMuxer = null;
        MediaExtractor vedioExtractor = null;
        MediaExtractor audioExtractor = null;
        MediaCodec vedioEncoder = null;
        MediaCodec vedioDecoder = null;
        MediaCodec audioEncoder = null;
        MediaCodec audioDecoder = null;
        try {
            File file = new File(outputVedioPath);
            if(!file.exists()) file.createNewFile();
        mediaMuxer = new MediaMuxer(file.toString(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        vedioExtractor = new MediaExtractor();
        vedioExtractor.setDataSource(Environment.getExternalStorageDirectory()+"/Video/demo.mp4");
        audioExtractor = new MediaExtractor();
        audioExtractor.setDataSource(Environment.getExternalStorageDirectory()+"/Video/demo.mp4");
        int vedioInputTrack = -1;
        for(int i=0;i<vedioExtractor.getTrackCount();i++){
            MediaFormat mediaFormat = vedioExtractor.getTrackFormat(i);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            if(mime.startsWith("video/")){
                vedioExtractor.selectTrack(i);
                vedioInputTrack = i;
                break;
            }
        }
        MediaFormat inputFormat = vedioExtractor.getTrackFormat(vedioInputTrack);
        inputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,OUTPUT_VIDEO_COLOR_FORMAT);
        if(mWidth != -1) {
            inputFormat.setInteger(MediaFormat.KEY_WIDTH,mWidth);
        }else{
            mWidth = inputFormat.getInteger(MediaFormat.KEY_WIDTH);
        }
        if(mHeight != -1){
            inputFormat.setInteger(MediaFormat.KEY_HEIGHT,mHeight);
        }else{
            mHeight = inputFormat.getInteger(MediaFormat.KEY_HEIGHT);
        }
        MediaCodecInfo vedioCodeInfo = null;
        int numCodes = MediaCodecList.getCodecCount();
        for(int i=0;i<numCodes;i++){
            MediaCodecInfo mediaCodecInfo = MediaCodecList.getCodecInfoAt(i);
            if(!mediaCodecInfo.isEncoder()) continue;
            String[] types = mediaCodecInfo.getSupportedTypes();
            for(int j=0;j<types.length;j++){
                if(types[j].equalsIgnoreCase(OUTPUT_VIDEO_MIME_TYPE)){
                    vedioCodeInfo = mediaCodecInfo;
                    break;
                }
            }
            if(vedioCodeInfo != null)break;
        }
        if(vedioCodeInfo == null) return;

        MediaFormat vedioOutputFormat = MediaFormat.createVideoFormat(OUTPUT_VIDEO_MIME_TYPE,mWidth,mHeight);
        vedioOutputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,OUTPUT_VIDEO_COLOR_FORMAT);
        vedioOutputFormat.setInteger(MediaFormat.KEY_BIT_RATE,OUTPUT_VIDEO_BIT_RATE);
        vedioOutputFormat.setInteger(MediaFormat.KEY_FRAME_RATE,OUTPUT_VIDEO_FRAME_RATE);
        vedioOutputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,OUTPUT_VIDEO_IFRAME_INTERVAL);
        vedioDecoder = MediaCodec.createDecoderByType(getMimeType(inputFormat));
        vedioDecoder.configure(inputFormat,null,null,0);
        vedioDecoder.start();
        vedioEncoder = MediaCodec.createByCodecName(vedioCodeInfo.getName());
        vedioEncoder.configure(vedioOutputFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        vedioEncoder.start();
        int inputAudioTrack = -1;
        for(int i=0;i<audioExtractor.getTrackCount();i++){
            MediaFormat mediaFormat = audioExtractor.getTrackFormat(i);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            if(mime.startsWith("audio/")){
                audioExtractor.selectTrack(i);
                inputAudioTrack = i;
                break;
            }
        }
        MediaFormat audioInputFormat = audioExtractor.getTrackFormat(inputAudioTrack);
        OUTPUT_AUDIO_SAMPLE_RATE_HZ = audioInputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        OUTPUT_AUDIO_CHANNEL_COUNT = audioInputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        MediaCodecInfo audioCodeInfo = null;
        int nums = MediaCodecList.getCodecCount();
        for(int i=0;i<nums;i++){
            MediaCodecInfo mediaCodecInfo = MediaCodecList.getCodecInfoAt(i);
            if(!mediaCodecInfo.isEncoder())continue;
            String[] types = mediaCodecInfo.getSupportedTypes();
            for(int j=0;j<types.length;j++){
                if(types[j].equalsIgnoreCase(OUTPUT_AUDIO_MIME_TYPE)){
                    audioCodeInfo = mediaCodecInfo;
                    break;
                }
            }
            if(audioCodeInfo != null) break;
        }
        if(audioCodeInfo == null) return;
        MediaFormat audioOutputFormat = MediaFormat.createAudioFormat(OUTPUT_AUDIO_MIME_TYPE,OUTPUT_AUDIO_SAMPLE_RATE_HZ,OUTPUT_AUDIO_CHANNEL_COUNT);
        audioOutputFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,100*1024);
        audioOutputFormat.setInteger(MediaFormat.KEY_BIT_RATE,OUTPUT_AUDIO_BIT_RATE);
        audioOutputFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,OUTPUT_AUDIO_AAC_PROFILE);
        audioDecoder = MediaCodec.createDecoderByType(getMimeType(audioInputFormat));
        audioDecoder.configure(audioInputFormat,null,null,0);
        audioDecoder.start();
        audioEncoder = MediaCodec.createByCodecName(audioCodeInfo.getName());
        audioEncoder.configure(audioOutputFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioEncoder.start();
            doExtractDecodeEncodeMux(vedioExtractor, audioExtractor,
                    vedioDecoder, vedioEncoder, audioDecoder, audioEncoder,
                    mediaMuxer);
        }catch (Exception e){
            e.printStackTrace();
            Log.i("wanlijun",e.toString());
        }finally {
            if(vedioExtractor != null){
                vedioExtractor.release();
            }
            if(audioExtractor != null){
                audioExtractor.release();
            }
            if(vedioDecoder!= null){
                vedioDecoder.stop();
                vedioDecoder.release();
            }
            if(vedioEncoder != null){
                vedioEncoder.stop();
                vedioEncoder.release();
            }
            if(audioDecoder != null){
                audioDecoder.stop();
                audioDecoder.release();
            }
            if(audioEncoder != null){
                audioEncoder.stop();
                audioEncoder.release();
            }
            if(mediaMuxer != null){
                mediaMuxer.stop();
                mediaMuxer.release();
            }
        }
    }
    private String getMimeType(MediaFormat format){
        return format.getString(MediaFormat.KEY_MIME);
    }
    private void showSupportedColorFormat(MediaCodecInfo.CodecCapabilities capabilities){
        for(int i : capabilities.colorFormats){
            Log.i("wanlijun","supported color format:"+i);
        }
    }
    private void splitFile(){
        String path = Environment.getExternalStorageDirectory() + "/Video/demo.mp4";
        try {
            byte[] buffer = new byte[10240];
            File file = new File(path);
            InputStream is = new FileInputStream(path);
            int fileSize = is.available();
            RandomAccessFile randomAccessFile = new RandomAccessFile(Environment.getExternalStorageDirectory() +"/temp.mp4","rwd");
            int len = 0;
            int count = 1;
            while ((len=is.read(buffer))!= -1 && count < 1024){
                Log.i("wanlijun","count="+count);
                randomAccessFile.write(buffer,0,len);
                count ++;
            }
            is.close();
            randomAccessFile.close();
        }catch (Exception e){
            e.printStackTrace();
            Log.i("wanlijun",e.toString());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == 101 && grantResults.length > 0){
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                try {
                    muxAudioAndVedio();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }else{
                Toast.makeText(MainActivity.this,"权限被拒绝",Toast.LENGTH_LONG).show();
            }
        }
    }
    /**
     * Does the actual work for extracting, decoding, encoding and muxing.
     */
    private void doExtractDecodeEncodeMux(MediaExtractor videoExtractor,
                                          MediaExtractor audioExtractor, MediaCodec videoDecoder,
                                          MediaCodec videoEncoder, MediaCodec audioDecoder,
                                          MediaCodec audioEncoder, MediaMuxer muxer) {
        ByteBuffer[] videoDecoderInputBuffers = null;
        ByteBuffer[] videoDecoderOutputBuffers = null;
        ByteBuffer[] videoEncoderInputBuffers = null;
        ByteBuffer[] videoEncoderOutputBuffers = null;
        MediaCodec.BufferInfo videoDecoderOutputBufferInfo = null;
        MediaCodec.BufferInfo videoEncoderOutputBufferInfo = null;
        if (mCopyVideo) {
            videoDecoderInputBuffers = videoDecoder.getInputBuffers();
            videoDecoderOutputBuffers = videoDecoder.getOutputBuffers();
            videoEncoderInputBuffers = videoEncoder.getInputBuffers();
            videoEncoderOutputBuffers = videoEncoder.getOutputBuffers();
            videoDecoderOutputBufferInfo = new MediaCodec.BufferInfo();
            videoEncoderOutputBufferInfo = new MediaCodec.BufferInfo();
        }
        ByteBuffer[] audioDecoderInputBuffers = null;
        ByteBuffer[] audioDecoderOutputBuffers = null;
        ByteBuffer[] audioEncoderInputBuffers = null;
        ByteBuffer[] audioEncoderOutputBuffers = null;
        MediaCodec.BufferInfo audioDecoderOutputBufferInfo = null;
        MediaCodec.BufferInfo audioEncoderOutputBufferInfo = null;
        if (mCopyAudio) {
            audioDecoderInputBuffers = audioDecoder.getInputBuffers();
            audioDecoderOutputBuffers = audioDecoder.getOutputBuffers();
            audioEncoderInputBuffers = audioEncoder.getInputBuffers();
            audioEncoderOutputBuffers = audioEncoder.getOutputBuffers();
            audioDecoderOutputBufferInfo = new MediaCodec.BufferInfo();
            audioEncoderOutputBufferInfo = new MediaCodec.BufferInfo();
        }
        // We will get these from the decoders when notified of a format change.
        MediaFormat decoderOutputVideoFormat = null;
        MediaFormat decoderOutputAudioFormat = null;
        // We will get these from the encoders when notified of a format change.
        MediaFormat encoderOutputVideoFormat = null;
        MediaFormat encoderOutputAudioFormat = null;
        // We will determine these once we have the output format.
        int outputVideoTrack = -1;
        int outputAudioTrack = -1;
        // Whether things are done on the video side.
        boolean videoExtractorDone = false;
        boolean videoDecoderDone = false;
        boolean videoEncoderDone = false;
        // Whether things are done on the audio side.
        boolean audioExtractorDone = false;
        boolean audioDecoderDone = false;
        boolean audioEncoderDone = false;
        // The video decoder output buffer to process, -1 if none.
        int pendingVideoDecoderOutputBufferIndex = -1;
        // The audio decoder output buffer to process, -1 if none.
        int pendingAudioDecoderOutputBufferIndex = -1;

        boolean muxing = false;

        int videoExtractedFrameCount = 0;
        int videoDecodedFrameCount = 0;
        int videoEncodedFrameCount = 0;

        int audioExtractedFrameCount = 0;
        int audioDecodedFrameCount = 0;
        int audioEncodedFrameCount = 0;
        boolean mVideoConfig = false;
        boolean mainVideoFrame = false;
        long mLastVideoSampleTime = 0;
        long mVideoSampleTime = 0;

        boolean mAudioConfig = false;
        boolean mainAudioFrame = false;
        long mLastAudioSampleTime = 0;
        long mAudioSampleTime = 0;
        while (!interrupted&& ((mCopyVideo && !videoEncoderDone) || (mCopyAudio && !audioEncoderDone))) {
            //###########################Video###################################
            // Extract video from file and feed to decoder.
            // Do not extract video if we have determined the output format but
            // we are not yet ready to mux the frames.
            while (mCopyVideo && !videoExtractorDone
                    && (encoderOutputVideoFormat == null || muxing)) {
                int decoderInputBufferIndex = videoDecoder
                        .dequeueInputBuffer(TIMEOUT_USEC);
                if (decoderInputBufferIndex <= MediaCodec.INFO_TRY_AGAIN_LATER) {
                        Log.d("wanlijun", "no video decoder input buffer: "
                                + decoderInputBufferIndex);
                    break;
                }
                Log.d("wanlijun",
                        "video decoder dequeueInputBuffer: returned input buffer: "
                                + decoderInputBufferIndex);
                ByteBuffer decoderInputBuffer = videoDecoderInputBuffers[decoderInputBufferIndex];
                int size = videoExtractor.readSampleData(decoderInputBuffer, 0);
                if (videoExtractor.getSampleFlags() == MediaExtractor.SAMPLE_FLAG_SYNC) {
                    Log.d("wanlijun"," video decoder SAMPLE_FLAG_SYNC ");
                }
                long presentationTime = videoExtractor.getSampleTime();
                Log.d("wanlijun", "video extractor: returned buffer of size "
                        + size);
                Log.d("wanlijun", "video extractor: returned buffer for time "
                        + presentationTime);
                if (size > 0) {
                    videoDecoder.queueInputBuffer(decoderInputBufferIndex, 0,
                            size, presentationTime,
                            videoExtractor.getSampleFlags());
                }
                videoExtractorDone = !videoExtractor.advance();
                if (videoExtractorDone) {
                    Log.d("wanlijun", "video extractor: EOS");
                    videoDecoder.queueInputBuffer(decoderInputBufferIndex, 0,
                            0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
                videoExtractedFrameCount++;
                // We extracted a frame, let's try something else next.
                break;
            }
            //###########################Audio###################################
            // Extract audio from file and feed to decoder.
            // Do not extract audio if we have determined the output format but
            // we are not yet ready to mux the frames.
            while (mCopyAudio && !audioExtractorDone
                    && (encoderOutputAudioFormat == null || muxing)) {
                int decoderInputBufferIndex = audioDecoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (decoderInputBufferIndex <= MediaCodec.INFO_TRY_AGAIN_LATER) {
                   Log.d("wanlijun", "no audio decoder input buffer: "+decoderInputBufferIndex);
                    break;
                }
                Log.d("wanlijun", "audio decoder dequeueInputBuffer: returned input buffer: "
                        + decoderInputBufferIndex);
                ByteBuffer decoderInputBuffer = audioDecoderInputBuffers[decoderInputBufferIndex];
                int size = audioExtractor.readSampleData(decoderInputBuffer, 0);
                long presentationTime = audioExtractor.getSampleTime();
                Log.d("wanlijun", "audio extractor: returned buffer of size "
                        + size);
                Log.d("wanlijun", "audio extractor: returned buffer for time "
                        + presentationTime);
                if (size > 0) {
                    audioDecoder.queueInputBuffer(decoderInputBufferIndex, 0,
                            size, presentationTime,
                            audioExtractor.getSampleFlags());
                }
                audioExtractorDone = !audioExtractor.advance();
                if (audioExtractorDone) {
                    Log.d("wanlijun", "audio extractor: EOS");
                    audioDecoder.queueInputBuffer(decoderInputBufferIndex, 0,
                            0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
                audioExtractedFrameCount++;
                // We extracted a frame, let's try something else next.
                break;
            }

            // Poll output frames from the video decoder and feed the encoder.
            while (mCopyVideo && !videoDecoderDone
                    && pendingVideoDecoderOutputBufferIndex == -1
                    && (encoderOutputVideoFormat == null || muxing)) {
                int decoderOutputBufferIndex = videoDecoder.dequeueOutputBuffer(videoDecoderOutputBufferInfo,
                        TIMEOUT_USEC);
                if (decoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.d("wanlijun", "no video decoder output buffer");
                    break;
                }else if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    //do what for this?
                    decoderOutputVideoFormat = videoDecoder.getOutputFormat();
                    Log.d("wanlijun", "video decoder: output format changed: " + decoderOutputVideoFormat);
                    break;
                }else if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    Log.d("wanlijun", "video decoder: output buffers changed");
                    videoDecoderOutputBuffers = videoDecoder.getOutputBuffers();
                    break;
                }

                if ((videoDecoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    Log.d("wanlijun", "video decoder: codec config buffer");
                    videoDecoder.releaseOutputBuffer(decoderOutputBufferIndex,false);
                    break;
                }
                Log.d("wanlijun", "video decoder: returned buffer for time "
                        + videoDecoderOutputBufferInfo.presentationTimeUs);

                pendingVideoDecoderOutputBufferIndex = decoderOutputBufferIndex;
                videoDecodedFrameCount++;
                // We extracted a pending frame, let's try something else next.
                break;
            }

            // Feed the pending decoded audio buffer to the video encoder.
            while (mCopyVideo && pendingVideoDecoderOutputBufferIndex != -1) {
                Log.d("wanlijun","video decoder: attempting to process pending buffer: "
                        + pendingVideoDecoderOutputBufferIndex);
                int encoderInputBufferIndex = videoEncoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (encoderInputBufferIndex <= MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.d("wanlijun", "no video encoder input buffer: "
                            +encoderInputBufferIndex);
                    break;
                }
                Log.d("wanlijun", "video encoder: returned input buffer: "
                        + encoderInputBufferIndex);
                ByteBuffer encoderInputBuffer = videoEncoderInputBuffers[encoderInputBufferIndex];
                int size = videoDecoderOutputBufferInfo.size;
                long presentationTime = videoDecoderOutputBufferInfo.presentationTimeUs;
                Log.d("wanlijun", "video decoder: processing pending buffer: "
                        + pendingVideoDecoderOutputBufferIndex);
                Log.d("wanlijun", "video decoder: pending buffer of size " + size);
                Log.d("wanlijun", "video decoder: pending buffer for time "
                        + presentationTime);
                if (size >= 0) {

                    try {
                        ByteBuffer decoderOutputBuffer = videoDecoderOutputBuffers[pendingVideoDecoderOutputBufferIndex]
                                .duplicate();
                        decoderOutputBuffer
                                .position(videoDecoderOutputBufferInfo.offset);
                        decoderOutputBuffer
                                .limit(videoDecoderOutputBufferInfo.offset + size);
                        encoderInputBuffer.position(0);
                        encoderInputBuffer.put(decoderOutputBuffer);
                        //size not enable
                        videoEncoder.queueInputBuffer(encoderInputBufferIndex, 0,
                                size, presentationTime,
                                videoDecoderOutputBufferInfo.flags);
                    } catch (Exception e) {
                        // TODO: handle exception
                        e.printStackTrace();
                    }

                }
                videoDecoder.releaseOutputBuffer(
                        pendingVideoDecoderOutputBufferIndex, false);
                pendingVideoDecoderOutputBufferIndex = -1;
                if ((videoDecoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d("wanlijun", "video decoder: EOS");
                    videoDecoderDone = true;
                }
                // We enqueued a pending frame, let's try something else next.
                break;
            }
            // Poll frames from the video encoder and send them to the muxer.
            while (mCopyVideo && !videoEncoderDone
                    && (encoderOutputVideoFormat == null || muxing)) {
                // can not get avilabel outputBuffers?
                int encoderOutputBufferIndex = videoEncoder.dequeueOutputBuffer(videoEncoderOutputBufferInfo,
                        TIMEOUT_USEC);
                if (encoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.d("wanlijun", "no video encoder output buffer");
                    break;
                }else if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.d("wanlijun", "video encoder: output format changed");
                    if (outputVideoTrack >= 0) {
                        // fail("video encoder changed its output format again?");
                        Log.d("wanlijun","video encoder changed its output format again?");
                    }
                    encoderOutputVideoFormat = videoEncoder.getOutputFormat();
                    break;
                }else if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                   Log.d("wanlijun", "video encoder: output buffers changed");
                    videoEncoderOutputBuffers = videoEncoder.getOutputBuffers();
                    break;
                }

                // assertTrue("should have added track before processing output", muxing);
                Log.d("wanlijun", "video encoder: returned output buffer: "
                        + encoderOutputBufferIndex);
                Log.d("wanlijun", "video encoder: returned buffer of size "
                        + videoEncoderOutputBufferInfo.size);
                ByteBuffer encoderOutputBuffer = videoEncoderOutputBuffers[encoderOutputBufferIndex];
                if ((videoEncoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    Log.d("wanlijun", "video encoder: codec config buffer");
                    // Simply ignore codec config buffers.
                    mVideoConfig = true;
                    videoEncoder.releaseOutputBuffer(encoderOutputBufferIndex,false);
                    break;
                }


                if(mVideoConfig){
                    if(!mainVideoFrame){
                        mLastVideoSampleTime = videoEncoderOutputBufferInfo.presentationTimeUs;
                        mainVideoFrame = true;
                    }else{
                        if(mVideoSampleTime == 0){
                            mVideoSampleTime = videoEncoderOutputBufferInfo.presentationTimeUs - mLastVideoSampleTime;
                        }
                    }
                }
                videoEncoderOutputBufferInfo.presentationTimeUs = mLastVideoSampleTime + mVideoSampleTime;
                if (videoEncoderOutputBufferInfo.size != 0) {
                    muxer.writeSampleData(outputVideoTrack,
                            encoderOutputBuffer, videoEncoderOutputBufferInfo);
                    mLastVideoSampleTime = videoEncoderOutputBufferInfo.presentationTimeUs;
                }

                if ((videoEncoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d("wanlijun", "video encoder: EOS");
                    videoEncoderDone = true;
                }
                videoEncoder.releaseOutputBuffer(encoderOutputBufferIndex,
                        false);
                videoEncodedFrameCount++;
                // We enqueued an encoded frame, let's try something else next.
                break;
            }

            // Poll output frames from the audio decoder.
            // Do not poll if we already have a pending buffer to feed to the
            // encoder.
            while (mCopyAudio && !audioDecoderDone
                    && pendingAudioDecoderOutputBufferIndex == -1
                    && (encoderOutputAudioFormat == null || muxing)) {
                int decoderOutputBufferIndex = audioDecoder
                        .dequeueOutputBuffer(audioDecoderOutputBufferInfo,TIMEOUT_USEC);
                if (decoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.d("wanlijun", "no audio decoder output buffer");
                    break;
                }else if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    decoderOutputAudioFormat = audioDecoder.getOutputFormat();
                        Log.d("wanlijun", "audio decoder: output format changed: " + decoderOutputAudioFormat);
                    break;
                }else if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    Log.d("wanlijun", "audio decoder: output buffers changed");
                    audioDecoderOutputBuffers = audioDecoder.getOutputBuffers();
                    break;
                }

                Log.d("wanlijun", "audio decoder: returned output buffer: "
                        + decoderOutputBufferIndex);
                Log.d("wanlijun", "audio decoder: returned buffer of size "
                        + audioDecoderOutputBufferInfo.size);
                Log.d("wanlijun", "audio decoder: returned buffer for time "
                        + audioDecoderOutputBufferInfo.presentationTimeUs);

                if ((audioDecoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    Log.d("wanlijun", "audio decoder: codec config buffer");
                    audioDecoder.releaseOutputBuffer(decoderOutputBufferIndex,false);
                    break;
                }


                pendingAudioDecoderOutputBufferIndex = decoderOutputBufferIndex;
                audioDecodedFrameCount++;
                // We extracted a pending frame, let's try something else next.
                break;
            }

            // Feed the pending decoded audio buffer to the audio encoder.
            while (mCopyAudio && pendingAudioDecoderOutputBufferIndex != -1) {
                Log.d("wanlijun","audio decoder: attempting to process pending buffer: "+ pendingAudioDecoderOutputBufferIndex);
                int encoderInputBufferIndex = audioEncoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (encoderInputBufferIndex <= MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.d("wanlijun", "no audio encoder input buffer: "+encoderInputBufferIndex);
                    break;
                }
                Log.d("wanlijun", "audio encoder: returned input buffer: "+ encoderInputBufferIndex);
                ByteBuffer encoderInputBuffer = audioEncoderInputBuffers[encoderInputBufferIndex];
                int size = audioDecoderOutputBufferInfo.size;
                long presentationTime = audioDecoderOutputBufferInfo.presentationTimeUs;
                Log.d("wanlijun", "audio decoder: processing pending buffer: "+ pendingAudioDecoderOutputBufferIndex);
                Log.d("wanlijun", "audio decoder: pending buffer of size " + size);
                Log.d("wanlijun", "audio decoder: pending buffer for time "+ presentationTime);
                if (size >= 0) {
                    try {
                        ByteBuffer decoderOutputBuffer = audioDecoderOutputBuffers[pendingAudioDecoderOutputBufferIndex]
                                .duplicate();
                        decoderOutputBuffer
                                .position(audioDecoderOutputBufferInfo.offset);
                        decoderOutputBuffer
                                .limit(audioDecoderOutputBufferInfo.offset + size);
                        encoderInputBuffer.position(0);
                        encoderInputBuffer.put(decoderOutputBuffer);
                        audioEncoder.queueInputBuffer(encoderInputBufferIndex, 0,
                                audioDecoderOutputBufferInfo.offset + size, presentationTime,
                                audioDecoderOutputBufferInfo.flags);

                    } catch (Exception e) {
                        // TODO: handle exception
                        e.printStackTrace();
                    }

                }
                audioDecoder.releaseOutputBuffer(pendingAudioDecoderOutputBufferIndex, false);
                pendingAudioDecoderOutputBufferIndex = -1;
                if ((audioDecoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d("wanlijun", "audio decoder: EOS");
                    audioDecoderDone = true;
                }
                // We enqueued a pending frame, let's try something else next.
                break;
            }

            // Poll frames from the audio encoder and send them to the muxer.
            while (mCopyAudio && !audioEncoderDone
                    && (encoderOutputAudioFormat == null || muxing)) {
                int encoderOutputBufferIndex = audioEncoder
                        .dequeueOutputBuffer(audioEncoderOutputBufferInfo,TIMEOUT_USEC);
                if (encoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.d("wanlijun", "no audio encoder output buffer");
                    break;
                }else if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.d("wanlijun", "audio encoder: output format changed");
                    if (outputAudioTrack >= 0) {
                        // fail("audio encoder changed its output format again?");
                        Log.d("wanlijun","audio encoder changed its output format again?");
                    }
                    encoderOutputAudioFormat = audioEncoder.getOutputFormat();
                    break;
                }else if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    Log.d("wanlijun", "audio encoder: output buffers changed");
                    audioEncoderOutputBuffers = audioEncoder.getOutputBuffers();
                    break;
                }
                // assertTrue("should have added track before processing output",muxing);
                Log.d("wanlijun", "audio encoder: returned output buffer: "
                        + encoderOutputBufferIndex);
                Log.d("wanlijun", "audio encoder: returned buffer of size "
                        + audioEncoderOutputBufferInfo.size);
                ByteBuffer encoderOutputBuffer = audioEncoderOutputBuffers[encoderOutputBufferIndex];
                if ((audioEncoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    Log.d("wanlijun", "audio encoder: codec config buffer");
                    // Simply ignore codec config buffers.
                    mAudioConfig = true;
                    audioEncoder.releaseOutputBuffer(encoderOutputBufferIndex,false);
                    break;
                }
                Log.d("wanlijun", " audio encoder: returned buffer for time "
                        + audioEncoderOutputBufferInfo.presentationTimeUs);

                if(mAudioConfig){
                    if(!mainAudioFrame){
                        mLastAudioSampleTime = audioEncoderOutputBufferInfo.presentationTimeUs;
                        mainAudioFrame = true;
                    }else{
                        if(mAudioSampleTime == 0){
                            mAudioSampleTime = audioEncoderOutputBufferInfo.presentationTimeUs - mLastAudioSampleTime;
                        }
                    }
                }

                audioEncoderOutputBufferInfo.presentationTimeUs = mLastAudioSampleTime + mAudioSampleTime;
                if (audioEncoderOutputBufferInfo.size != 0) {
                    muxer.writeSampleData(outputAudioTrack,
                            encoderOutputBuffer, audioEncoderOutputBufferInfo);
                    mLastAudioSampleTime = audioEncoderOutputBufferInfo.presentationTimeUs;
                }

                if ((audioEncoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d("wanlijun", "audio encoder: EOS");
                    audioEncoderDone = true;
                }
                audioEncoder.releaseOutputBuffer(encoderOutputBufferIndex,false);
                audioEncodedFrameCount++;
                // We enqueued an encoded frame, let's try something else next.
                break;
            }

            if (!muxing && (!mCopyAudio || encoderOutputAudioFormat != null)
                    && (!mCopyVideo || encoderOutputVideoFormat != null)) {
                if (mCopyVideo) {
                    Log.d("wanlijun", "muxer: adding video track.");
                    outputVideoTrack = muxer.addTrack(encoderOutputVideoFormat);
                }
                if (mCopyAudio) {
                    Log.d("wanlijun", "muxer: adding audio track.");
                    outputAudioTrack = muxer.addTrack(encoderOutputAudioFormat);
                }
                Log.d("wanlijun", "muxer: starting");
                muxer.start();
                muxing = true;
            }
        }
        Log.d("wanlijun", "exit looper");
    }
}

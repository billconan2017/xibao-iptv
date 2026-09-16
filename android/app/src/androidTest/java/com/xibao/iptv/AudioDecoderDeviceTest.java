package com.xibao.iptv;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoderOutputBuffer;
import androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder;
import androidx.media3.decoder.ffmpeg.FfmpegLibrary;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class AudioDecoderDeviceTest {
    @Test public void mp2ProducesPcm() throws Exception {decode("tone.mp2",MimeTypes.AUDIO_MPEG_L2);}
    @Test public void aacProducesPcm() throws Exception {decode("tone.aac",MimeTypes.AUDIO_AAC);}
    @Test public void ac3ProducesPcm() throws Exception {decode("tone.ac3",MimeTypes.AUDIO_AC3);}
    @Test public void eac3ProducesPcm() throws Exception {decode("tone.eac3",MimeTypes.AUDIO_E_AC3);}
    private void decode(String file,String mime) throws Exception {
        assertTrue("Packaged native decoder must load on Android",FfmpegLibrary.isAvailable());
        assertTrue(mime,FfmpegLibrary.supportsFormat(mime));
        byte[] packet;
        try(InputStream stream=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(file)){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] block=new byte[4096];int read;
            while((read=stream.read(block))!=-1)bytes.write(block,0,read);packet=bytes.toByteArray();
        }
        Format.Builder formatBuilder=new Format.Builder().setSampleMimeType(mime).setSampleRate(48000).setChannelCount(2);
        if(MimeTypes.AUDIO_AAC.equals(mime)){
            // The Media3 ADTS extractor supplies AudioSpecificConfig separately
            // and removes the transport header before passing a frame to a decoder.
            int objectType=((packet[2]&0xc0)>>6)+1;
            int frequencyIndex=(packet[2]&0x3c)>>2;
            int channels=((packet[2]&1)<<2)|((packet[3]&0xc0)>>6);
            int config=(objectType<<11)|(frequencyIndex<<7)|(channels<<3);
            formatBuilder.setInitializationData(java.util.Collections.singletonList(new byte[]{(byte)(config>>8),(byte)config}));
            packet=java.util.Arrays.copyOfRange(packet,(packet[1]&1)==1?7:9,packet.length);
        }
        Format format=formatBuilder.build();
        FfmpegAudioDecoder decoder=new FfmpegAudioDecoder(format,2,2,16384,false);
        try {
            DecoderInputBuffer input=decoder.dequeueInputBuffer();assertNotNull(input);
            input.ensureSpaceForWrite(packet.length);input.data.put(packet);input.flip();decoder.queueInputBuffer(input);
            long deadline=android.os.SystemClock.elapsedRealtime()+5000;
            SimpleDecoderOutputBuffer output=null;
            while(output==null && android.os.SystemClock.elapsedRealtime()<deadline){output=decoder.dequeueOutputBuffer();if(output==null)Thread.sleep(10);}
            assertNotNull("Decoded output for "+mime,output);assertFalse(output.shouldBeSkipped);
            assertTrue("PCM output must not be empty",output.data.remaining()>0);
            boolean nonzero=false;while(output.data.hasRemaining())if(output.data.get()!=0)nonzero=true;
            assertTrue("Generated tone must not become silence for "+mime,nonzero);
            assertEquals(48000,decoder.getSampleRate());assertEquals(2,decoder.getChannelCount());
            output.release();
        }finally{decoder.release();}
    }
}

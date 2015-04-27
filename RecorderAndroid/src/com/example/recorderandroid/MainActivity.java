package com.example.recorderandroid;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.Chronometer;
import android.widget.TextView;

//http://stackoverflow.com/questions/18867933/record-audio-with-mediarecorder-and-play-it-simultaniously-with-mediaplayer
public class MainActivity extends ActionBarActivity {
	private Handler mChronHandler;
	private Chronometer mChronometer;
	private TextView mTxtTimer;
	public static final int SAMPLE_RATE = 16000;
	private AudioRecord mRecorder;
	private File mRecordingFile;
	private short[] mBuffer;
	private boolean mIsRecording;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mChronometer = (Chronometer)findViewById(R.id.chronometer);
		mTxtTimer = (TextView)findViewById(R.id.txtTimer);

		int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
				AudioFormat.ENCODING_PCM_16BIT);
		mBuffer = new short[bufferSize];
		mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
				AudioFormat.ENCODING_PCM_16BIT, bufferSize);

	}

	public void buttonClick(View v) {
		switch (v.getId()) {
		case R.id.btnStart:
			/*mRecorder = new MediaRecorder();
			mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
			mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);

			try {
				mRecorder.prepare();
			} catch (IOException e) {
				//Log.e(LOG_TAG, "prepare() failed");
			}

			mRecorder.start();

			mRecorder.setOutputFile(mRecordingFile.getAbsolutePath());
			mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);*/

			mIsRecording = true;
			mRecordingFile = new File(Environment.getExternalStorageDirectory(), "VoiceNotes");
			mRecordingFile.mkdirs();

			mRecordingFile = new File(mRecordingFile, System.currentTimeMillis() + ".raw");

			startBufferedWrite(mRecordingFile);

			int stoppedMilliseconds = 0;

			mChronometer.setText("00:00");
			String chronoText = mChronometer.getText().toString();
			String array[] = chronoText.split(":");
			if (array.length == 2) {
				stoppedMilliseconds = Integer.parseInt(array[0]) * 60 * 1000
						+ Integer.parseInt(array[1]) * 1000;
			} else if (array.length == 3) {
				stoppedMilliseconds = Integer.parseInt(array[0]) * 60 * 60 * 1000 
						+ Integer.parseInt(array[1]) * 60 * 1000
						+ Integer.parseInt(array[2]) * 1000;
			}

			mChronometer.setBase(SystemClock.elapsedRealtime() - stoppedMilliseconds);
			mChronometer.start();
			break;

		case R.id.btnStop:
			if(mIsRecording) {
				mRecorder.stop();
				mIsRecording = false;
				mChronometer.stop();

				File waveFile = new File(Environment.getExternalStorageDirectory(), "VoiceNotes");
				waveFile.mkdirs();

				waveFile = new File(waveFile, System.currentTimeMillis() + ".mp4");

				try {
					rawToWave(mRecordingFile, waveFile);
				} catch (IOException e) {

				}

			}
			break;

		default:
			break;
		}
	}

	Runnable r = new Runnable() {
		@Override
		public void run() {
			mChronometer.setText(String .valueOf(((SystemClock.elapsedRealtime() - mChronometer.getBase())/1000)%60) + 
					":" + 
					((SystemClock.elapsedRealtime() - mChronometer.getBase())/1000)/60);
			mChronHandler.postDelayed(r, 100);
		}
	};

	private void startBufferedWrite(final File file) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				DataOutputStream output = null;
				try {
					output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
					while (mIsRecording) {
						double sum = 0;
						int readSize = mRecorder.read(mBuffer, 0, mBuffer.length);
						for (int i = 0; i < readSize; i++) {
							output.writeShort(mBuffer[i]);
							sum += mBuffer[i] * mBuffer[i];
						}

						MainActivity.this.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								mTxtTimer.setText(mChronometer.getText());
							}
						});

						MediaPlayer mediaPlayer = MediaPlayer.create(MainActivity.this,
								Uri.parse(file.getAbsolutePath())); 
						if (readSize > 0) {
							mTxtTimer.setText((mediaPlayer.getDuration() / 1000) % 60 + ":" + 
									(mediaPlayer.getDuration() / 1000 ) / 60);
						}
					}
				} catch (IOException e) {
				} finally {
					if (output != null) {
						try {
							output.flush();
						} catch (IOException e) {
						} finally {
							try {
								output.close();
							} catch (IOException e) {
							}
						}
					}
				}
			}
		}).start();
	}

	private void rawToWave(final File rawFile, final File waveFile) throws IOException {

		byte[] rawData = new byte[(int) rawFile.length()];
		DataInputStream input = null;
		try {
			input = new DataInputStream(new FileInputStream(rawFile));
			input.read(rawData);
		} finally {
			if (input != null) {
				input.close();
			}
		}

		DataOutputStream output = null;
		try {
			output = new DataOutputStream(new FileOutputStream(waveFile));
			// WAVE header
			// see http://ccrma.stanford.edu/courses/422/projects/WaveFormat/
			//http://www.topherlee.com/software/pcm-tut-wavformat.html
			writeString(output, "RIFF"); // chunk id//Marks the file as a riff file. Characters are each 1 byte long.
			writeInt(output, 36 + rawData.length); // chunk size//Size of the overall file - 8 bytes, in bytes (32-bit integer). Typically, you'd fill this in after creation.
			writeString(output, "WAVE"); // format//File Type Header. For our purposes, it always equals "WAVE".
			writeString(output, "fmt "); // subchunk 1 id//Format chunk marker. Includes trailing null
			writeInt(output, 16); // subchunk 1 size//Length of format data
			writeShort(output, (short) 1); // audio format (1 = PCM)//Type of format (1 is PCM) - 2 byte integer
			writeShort(output, (short) 1); // number of channels//Number of Channels - 2 byte integer
			writeInt(output, SAMPLE_RATE); // sample rate
			writeInt(output, SAMPLE_RATE * 2); // byte rate (Sample Rate * BitsPerSample * Channels) / 8.
			writeShort(output, (short) 2); // block align BitsPerSample * Channels) / 8.1 - 8 bit mono2 - 8 bit stereo/16 bit mono4 - 16 bit stereo
			writeShort(output, (short) 16); // bits per sample
			writeString(output, "data"); // subchunk 2 id "data" chunk header. Marks the beginning of the data section.
			writeInt(output, rawData.length); // subchunk 2 size Size of the data section.
			// Audio data (conversion big endian -> little endian)
			short[] shorts = new short[rawData.length / 2];
			ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
			ByteBuffer bytes = ByteBuffer.allocate(shorts.length * 2);
			for (short s : shorts) {
				bytes.putShort(s);
			}
			output.write(bytes.array());
		} finally {
			if (output != null) {
				output.close();
			}
		}
	}

	private void writeInt(final DataOutputStream output, final int value) throws IOException {
		output.write(value >> 0);
		output.write(value >> 8);
		output.write(value >> 16);
		output.write(value >> 24);
	}

	private void writeShort(final DataOutputStream output, final short value) throws IOException {
		output.write(value >> 0);
		output.write(value >> 8);
	}

	private void writeString(final DataOutputStream output, final String value) throws IOException {
		for (int i = 0; i < value.length(); i++) {
			output.write(value.charAt(i));
		}
	}
}

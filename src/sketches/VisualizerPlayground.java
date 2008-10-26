package sketches;

import processing.core.PApplet;

import ddf.minim.Minim;
import ddf.minim.AudioPlayer;

import ddf.minim.analysis.FFT;
import ddf.minim.analysis.BeatDetect;

import megamu.shapetween.Shaper;
import megamu.shapetween.Tween;

public class VisualizerPlayground extends PApplet {
	private static final long serialVersionUID = 1L;
	
	private static final int BINS = 30;

	private static final int[] screenDimensions = new int[] { 640, 480 };
//	private static final int[] screenDimensions = new int[] { 1400, 1050 };
//	private static final int[] screenDimensions = new int[] { 1600, 1200 };
	private static final int halfHeight = screenDimensions[1] / 2;
	
	private static final float[] periodMillis = new float[] { 50, 500 };
//	private static final float[] periodMillis = new float[] { 100, 500 };
//	private static final float[] periodMillis = new float[] { 200, 1000 };
//	private static final float[] periodMillis = new float[] { 250, 1000 };
//	private static final float[] periodMillis = new float[] { 500, 750 };
	
	private final int backgroundColor = color(255);
	private final int instantGreaterColor = color(0, 128, 0, 128);
//	private final int interpColor = color(0, 0, 0, 128);
//	private final int interpColor = color(0, 128, 0);
	private final int interpColor = color(0, 0, 0, 64);
//	private final int instantLesserColor = instantGreaterColor;
//	private final int instantLesserColor = color(0, 0, 0, 64);
	private final int instantLesserColor = color(0, 0, 0, 128);
		
//	private static final String audioFilename = "0124.mp3";
//	private static final String audioFilename = "jitney2.mp3";
//	private static final String audioFilename = "wecarryon.mp3";
//	private static final String audioFilename = "machinegun.mp3";
//	private static final String audioFilename = "therip.mp3";
//	private static final String audioFilename = "marcus_kellis_theme.mp3";
//	private static final String audioFilename = "chewinggum.mp3";
//	private static final String audioFilename = "moilolita.mp3";
//	private static final String audioFilename = "battlecry.mp3";
//	private static final String audioFilename = "leyendecker.mp3";
	private static final String[] audioFilenames = new String[] {
		"windowlicker.mp3",
		"leyendecker.mp3",
		"battlecry.mp3",
		"chewinggum.mp3",
		"marcus_kellis_theme.mp3",
		"moilolita.mp3",
		"machinegun.mp3",
		"wecarryon.mp3",
		"therip.mp3",
		"0124.mp3",
		"jitney2.mp3"
	};
	int audioFilenameIndex = audioFilenames.length - 1;
	
	private AudioPlayer player;
	
	private FFT[] fftWorkers;
	private int fftIndex;
	
	private BeatDetect beat;
	
	private float barWidth;
	private float lastSampleMillis;
	private float maxSpectrumValue;
	
	private Tween transition;
	
	static public void main(String args[]) {
		PApplet.main(new String[] { "--display=2", /*"--present",*/ "sketches.VisualizerPlayground" });
	}

	public void setup() {
		size(screenDimensions[0], screenDimensions[1], OPENGL);
		
		Minim.start(this);
		
		player = Minim.loadFile(audioFilenames[audioFilenameIndex]);

		fftWorkers = new FFT[] {
			new FFT(player.mix.size(), player.sampleRate()),
			new FFT(player.mix.size(), player.sampleRate())
		};
		for (FFT worker : fftWorkers) {
//			worker.linAverages(BINS);
			worker.logAverages(22, 3);
		}
		fftIndex = 0;
		
		barWidth = (float)width / fftWorkers[fftIndex].avgSize();
		lastSampleMillis = MIN_FLOAT;
		
		float sum = 0;
		for (float millis : periodMillis) sum += millis;
		transition = new Tween(this, /*(sum / periodMillis.length)*/periodMillis[periodMillis.length - 1] / 1000, Tween.SECONDS, Shaper.QUADRATIC);
		
		beat = new BeatDetect();
//		beat.setSensitivity((int)(sum / periodMillis.length));
//		beat.setSensitivity((int)(periodMillis[periodMillis.length - 1]));
		beat.setSensitivity((int)(periodMillis[0]));
		
		frameRate(30);
		
		stroke(255);
		strokeWeight((float)height / 96);
		background(backgroundColor);
	}
	
	public void draw() {
		background(backgroundColor);
		
		beat.detect(player.mix);
		
		// If there's been a beat and it's been long enough since the last
		// sample, or a sample hasn't been taken for the maximum allowed time,
		// take another one, and re-start the spectrum-transition Tween.
		float currentMillis = millis();
		float sampleDiff = currentMillis - lastSampleMillis;
		if ((sampleDiff > periodMillis[0] && beat.isOnset()) || sampleDiff > periodMillis[periodMillis.length - 1]) {			
			FFT fft = nextFFT();
			fft.forward(player.mix);
		
			// Get maximum frequency amplitude for normalization purposes
//			maxSpectrumValue = 0;
//			for (int i = 0; i < fft.avgSize(); ++i) {
//				maxSpectrumValue = max(maxSpectrumValue, fft.getAvg(i));
//			}
			
			lastSampleMillis = currentMillis;
			
			transition.start();
		}
		
		drawSpectrum();
	}
	
	public void mousePressed() {
//		if (player.isPlaying()) {
//			player.pause();
//		} else {
//			player.play();
//		}
		
		player.close();
		
		audioFilenameIndex = (audioFilenameIndex + 1) % audioFilenames.length;
	
		player = Minim.loadFile(audioFilenames[audioFilenameIndex]);

		fftWorkers[0] = new FFT(player.mix.size(), player.sampleRate());
		fftWorkers[1] = new FFT(player.mix.size(), player.sampleRate());
		for (FFT worker : fftWorkers) {
//			worker.linAverages(BINS);
			worker.logAverages(22, 3);
		}
		fftIndex = 0;
		
		player.play();
	}
	
	private void drawSpectrum() {
		float temp, prevTemp;
//		float scalar = maxSpectrumValue * 2;
//		float scalar = 0.5f;
//		float scalar = 1f;
		float scalar = 0.005f;
		
		FFT fftCurrent = getActiveFFT();
		FFT fftPrevious = getPreviousFFT();
		
		float currentMag, previousMag;
		int size = fftCurrent.avgSize();
		for (int i = 0; i < size; ++i) {			
//			currentMag = fftCurrent.getAvg(i);
//			previousMag = fftPrevious.getAvg(i);
			currentMag = getCombinedMagnitude(fftCurrent, size, i);
			previousMag = getCombinedMagnitude(fftPrevious, size, i);
			
			// Draw greater spectrum bars
//			temp = max(currentMag, previousMag) * scalar;
//			fill(instantGreaterColor);
//			drawBar(i, temp);
			
			// Draw interpolating spectrum bars
			temp = lerp(previousMag, currentMag, transition.position()) * scalar;
			fill(interpColor);
			drawBar(i, temp);
			
			// Draw lesser spectrum bars
//			temp = min(currentMag, previousMag) * scalar;
//			fill(instantLesserColor);
//			drawBar(i, temp);
			
			// Draw instantaneous bars for changed spectrum bands
//			temp = fftCurrent.getAvg(i) * scalar;
//			prevTemp = fftPrevious.getAvg(i) * scalar;
//			if (temp != prevTemp) {
//				if (temp > prevTemp || !transition.isTweening()) {
//					fill(instantGreaterColor);
//				} else {
//					fill(instantLesserColor);
//				}
//				drawBar(i, temp);
//			}
		}
	}
	
	private float getCombinedMagnitude(FFT fft, int size, int i) {
		return height * (fft.getAvg(i) + fft.getAvg(size - 1 - i)) / 2;  
	}
	
	private void drawBar(int pos, float magnitude) {
		rect(pos * barWidth, halfHeight - magnitude / 2, barWidth, magnitude);
	}
	
	private FFT nextFFT() {
		fftIndex = ++fftIndex % fftWorkers.length;
		return fftWorkers[fftIndex];
	}
	
	private FFT getActiveFFT() {
		return fftWorkers[fftIndex];
	}
	
	private FFT getPreviousFFT() {
		int temp = fftIndex - 1;
		return fftWorkers[(temp < 0) ? fftWorkers.length - 1 : temp];
	}
}
package sketches;

import processing.core.PApplet;
import processing.core.PImage;

import toxi.video.capture.SimpleCapture;
import toxi.video.capture.JMFSimpleCapture;

import blobDetection.BlobDetection;
import blobDetection.Blob;

import Jama.Matrix;

public class BlobsPlayground extends PApplet {
	private static final long serialVersionUID = 1L;

	String CAPTURE_DEVICE = "vfw:Microsoft WDM Image Capture (Win32):0";
	int CAPTURE_WIDTH = 320;
	int CAPTURE_HEIGHT = 240;
	int CAPTURE_FPS = 90;
	boolean CAPTURE_FLIP_X = true;
	boolean CAPTURE_FLIP_Y = false;
	
	float BLOB_THRESH = 0.075f;
	
	int SCREEN_WIDTH = 1280;
	int SCREEN_HEIGHT = 1024;
	
	int[] backgroundColors = new int[] {
		color(255),
		color(0)
	};
	int backgroundIndex = 0;

	SimpleCapture capture;
	
	BlobDetection bd;
	
	private int[] diffPixels;
	private int[] backgroundPixels;
	private boolean resetBackground;
	
	private Matrix calibrationMatrix;
	private double[][] rawCalibratedPoints;
	
	private double[][] calibrationPoints;
	private int calibrationIndex;
	
	private double alphaX, betaX, deltaX;
	private double alphaY, betaY, deltaY;
	
	boolean calibrated = false;
	
	float lastX = -1;
	float lastY = -1;
	
	public static void main(String args[]) {
		PApplet.main(new String[] { "--display=2", "--present", "sketches.BlobsPlayground" });
	}

	public void setup() {
		size(SCREEN_WIDTH, SCREEN_HEIGHT, OPENGL);
		capture = new JMFSimpleCapture();

//		JMFSimpleCapture.listDevices(); // short version

		// this will dump all available details for each device and format
//		JMFSimpleCapture.listDevices(System.out,true);

		if (!capture.initVideo(CAPTURE_DEVICE, CAPTURE_WIDTH, CAPTURE_HEIGHT, 30)) {
			println(capture.getError());
			
			// you might have to (re-)run the JMFRegistry application
			// devices sometimes are unrecognized
			System.exit(0);
		}
		
		bd = new BlobDetection(CAPTURE_WIDTH, CAPTURE_HEIGHT);
		bd.setPosDiscrimination(true);
		bd.setThreshold(BLOB_THRESH);
		
		frameRate(CAPTURE_FPS);
		
		diffPixels = new int[CAPTURE_WIDTH * CAPTURE_HEIGHT];
		backgroundPixels = new int[CAPTURE_WIDTH * CAPTURE_HEIGHT];
		resetBackground = true;
		
		rawCalibratedPoints = new double[][] {
			{ -1, -1, 1},
			{ -1, -1, 1},
			{ -1, -1, 1}
		};
		
		calibrationPoints = new double[][] {
			{ width * 0.10, height * 0.90 },
			{ width * 0.10, height * 0.10 },
			{ width * 0.90, height * 0.10 }
		};
		calibrationIndex = 0;
		
		strokeWeight(3);
		stroke(255, 0, 0);
		noFill();
	}

	public void draw() {
		if (!calibrated) background(backgroundColors[backgroundIndex]);
		
		PImage frame = capture.getFrame();
		if (resetBackground) {
			arraycopy(frame.pixels, backgroundPixels);
			resetBackground = false;
		}
		
		subtractBackground(frame.pixels);
		bd.computeBlobs(diffPixels);
		Blob blob;
		int numBlobs = bd.getBlobNb();
		
		if (calibrationIndex > 0 && numBlobs > 0) {
			blob = bd.getBlob(0);
			rawCalibratedPoints[calibrationIndex - 1][0] = ((blob.xMax + blob.xMin) / 2) * CAPTURE_WIDTH;
			rawCalibratedPoints[calibrationIndex - 1][1] = ((blob.yMax + blob.yMin) / 2) * CAPTURE_HEIGHT;
			
			calcCalibrationCoefficients();
			
			calibrationIndex = 0;
		}
		
		// scale and flip appropriately
		pushMatrix();
		{
			if (CAPTURE_FLIP_X) {
		        translate(width / 2, 0, 0);
		        rotateY(PI);
		        translate(-width / 2, 0, 0);
			}
			if (CAPTURE_FLIP_Y) {
		        translate(height / 2, 0, 0);
		        rotateX(PI);
		        translate(-height / 2, 0, 0);
			}
		
			scale((float)width / CAPTURE_WIDTH, (float)height / CAPTURE_HEIGHT);
			
			pushMatrix();
			{
				translate(0, 0, 0.001f);
				image(frame, 0, 0);
			}
			popMatrix();
			
			if (!calibrated) {
				for (int i = 0; i < numBlobs; ++i) {
					blob = bd.getBlob(i);
					
					rect(
						blob.xMin * CAPTURE_WIDTH, blob.yMin * CAPTURE_HEIGHT,
						blob.w * CAPTURE_WIDTH, blob.h * CAPTURE_HEIGHT
					);
				}
			}
		}
		popMatrix();
		
		if (calibrated) {
			for (int i = 0; i < numBlobs; ++i) {
				blob = bd.getBlob(i);
				
				float rawX = ((blob.xMin + blob.xMax) / 2) * CAPTURE_WIDTH;
				float rawY = ((blob.yMin + blob.yMax) / 2) * CAPTURE_HEIGHT;
				float screenX = (float)alphaX * rawX + (float)betaX * rawY + (float)deltaX;
				float screenY = (float)alphaY * rawX + (float)betaY * rawY + (float)deltaY;
				
				ellipse(screenX, screenY, 40, 40);
				
				if (lastX >= 0 && lastY >= 0) {
//					line(lastX, lastY, screenX, screenY);
				}
				
				lastX = screenX;
				lastY = screenY;
			}
		}
		
		if (!calibrated) drawCalibrationTargets();
	}

	public void stop() {
		capture.shutdown();
		super.stop();
	}
	
	public void keyPressed() {
		if (key == '1') {
			calibrationIndex = 1;
		} else if (key == '2') {
			calibrationIndex = 2;
		} else if (key == '3') {
			calibrationIndex = 3;
		} else if (key == 'b') {
			backgroundIndex = (backgroundIndex + 1) % backgroundColors.length;
		} else if (key == 'c') {
			calibrated = false;
		} else {
			resetBackground = true;
		}
	}
	
	private void drawCalibrationTargets() {
		float diameter = 40;
		for (int i = 0; i < calibrationPoints.length; ++i) {
			ellipse((float)calibrationPoints[i][0], (float)calibrationPoints[i][1], diameter * 0.25f, diameter * 0.25f);
			ellipse((float)calibrationPoints[i][0], (float)calibrationPoints[i][1], diameter * 0.50f, diameter * 0.50f);
			ellipse((float)calibrationPoints[i][0], (float)calibrationPoints[i][1], diameter * 0.75f, diameter * 0.75f);
			ellipse((float)calibrationPoints[i][0], (float)calibrationPoints[i][1], diameter, diameter);
		}
	}
	
	private void subtractBackground(int[] frame) {
		// For each pixel in the video frame...
		for (int i = 0; i < frame.length; ++i) {
			// Fetch the current color in that location, and also the color
			// of the background in that spot
			int currColor = frame[i];
			int bkgdColor = backgroundPixels[i];
			
			// Extract the components of the current pixel's color
			int currR = (currColor >> 16) & 0xFF;
			int currG = (currColor >> 8) & 0xFF;
			int currB = currColor & 0xFF;
			
			// Extract the components of the background pixel's color
			int bkgdR = (bkgdColor >> 16) & 0xFF;
			int bkgdG = (bkgdColor >> 8) & 0xFF;
			int bkgdB = bkgdColor & 0xFF;
			
			// Compute the difference of the red, green, and blue values
			int diffR = abs(currR - bkgdR);
			int diffG = abs(currG - bkgdG);
			int diffB = abs(currB - bkgdB);
			
			// Write the difference to the difference-image array
			diffPixels[i] = 0xFF000000 | (diffR << 16) | (diffG << 8) | diffB;
		}
	}
	
	private void calcCalibrationCoefficients() {
		boolean unset = false;
		for (int i = 0; i < rawCalibratedPoints.length; ++i) {
			for (int j = 0; j < rawCalibratedPoints[i].length; ++j) {
				if (rawCalibratedPoints[i][j] < 0) {
					unset = true;
					break;
				}
			}
			if (unset) break;
		}
		
		if (!unset) {
			Matrix xPoints = new Matrix(new double[][] {
				{ calibrationPoints[0][0] },
				{ calibrationPoints[1][0] },
				{ calibrationPoints[2][0] }
			});
			Matrix yPoints = new Matrix(new double[][] {
				{ calibrationPoints[0][1] },
				{ calibrationPoints[1][1] },
				{ calibrationPoints[2][1] }
			});
			calibrationMatrix = new Matrix(rawCalibratedPoints);
			
			Matrix inverse = calibrationMatrix.inverse();
			Matrix xCoef = inverse.times(xPoints);
			Matrix yCoef = inverse.times(yPoints);
			
			alphaX = xCoef.get(0, 0);
			betaX = xCoef.get(1, 0);
			deltaX = xCoef.get(2, 0);
			
			alphaY = yCoef.get(0, 0);
			betaY = yCoef.get(1, 0);
			deltaY = yCoef.get(2, 0);
			
			calibrated = true;
		}
	}
}
package sketches;

import geomerative.RCommand;
import geomerative.RContour;
import geomerative.RFont;
import geomerative.RGeomElem;
import geomerative.RGroup;
import geomerative.RMatrix;
import geomerative.RPoint;
import geomerative.RPolygon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import java.util.concurrent.ArrayBlockingQueue;

import processing.core.PApplet;

public class WordsSpace extends PApplet {
	private static final long serialVersionUID = 1L;
	
	protected PApplet APP;
	
	private static final Random RANDOM = new Random();
	
	private static final float ROT_LIMIT = PI / 2;
	
	private static final int MOTION_FACTOR = 3;
	
	private static final int LIFETIME_COEFFICIENT = 7;
	
	private final int[] colorsSMPTE = new int[] {
		color(192, 192,   0),
		color(  0, 192, 192),
		color(  0, 192,   0),
		color(192,   0,   0),
		color(  0,   0, 192)
	};
	private final int[] colorsSpringHappiness = new int[] {
		color(175, 117, 117),
		color(239, 216, 161),
		color(188, 214, 147),
		color(175, 215, 219),
		color( 61, 156, 168)
	};
	
	int maxNumWords;
	int maxAgeInFrames;
	long maxAgeInMillis;

	int defaultBackgroundColor;
	int defaultBaseColor;
	int defaultMaskColor;
	float defaultFadeInIncrement;
	float defaultFadeOutIncrement;
	
	ArrayList<File> availableWords;
	List<WordState> activeWords;
	ArrayBlockingQueue<WordState> wordsQueue;
	int queueBufferLength;
	List<WordState> retiredWords;
	ArrayBlockingQueue<RPoint> availableLocations;
	ArrayBlockingQueue<Integer> availableColors;
	
	LinkedList<Thread> wordBuilderThreads;
	LinkedList<Thread> deadThreads;
	
	static public void main(String args[]) {
		PApplet.main(new String[] { "--display=1", "--present", "sketches.WordsSpace" });
	}

	public void setup() {

		defaultBackgroundColor = color(0, 0, 0);
//		defaultBackgroundColor = color(255);

//		defaultBaseColor = color(128, 128, 128, 128);
//		defaultBaseColor = color(200, 0, 0, 0);
		defaultBaseColor = color(200, 200, 200, 0);
//		defaultBaseColor = color(0, 0, 0, 0);
//		defaultBaseColor = color(0);
		
//		defaultMaskColor = color(128, 128, 128, 128);
		defaultMaskColor = color(255);
//		defaultMaskColor = color(0);

		defaultFadeInIncrement = 5;
		defaultFadeOutIncrement = 20;
		
		APP = this;

//		size(800, 600, OPENGL);
		size(1024, 768, OPENGL);
//		size(1400, 1050, OPENGL);
//		size(1600, 1200, OPENGL);
		
		frame.setLocation(0, 0);
		background(defaultBackgroundColor);
		
		RCommand.setSegmentator(RCommand.UNIFORMSTEP);
		RCommand.setSegmentStep(0.15f);
		
		strokeWeight(3);
		noStroke();
		smooth();
		
		maxNumWords = 5;
		maxAgeInFrames = 150;
		maxAgeInMillis = 10 * 1000;
		queueBufferLength = 5;
		wordsQueue = new ArrayBlockingQueue<WordState>(maxNumWords + queueBufferLength);
		retiredWords = new LinkedList<WordState>();
		
		activeWords = new LinkedList<WordState>();
		
		availableWords = new ArrayList<File>();
//		findWords(sketchPath("src/data/congruent_antonyms/verb"), availableWords);
		findWords(sketchPath("src/data/chosen_antonyms"), availableWords);
		
		availableLocations = new ArrayBlockingQueue<RPoint>(maxNumWords);
		for (int i = 0; i < maxNumWords; ++i) {
			availableLocations.add(new RPoint((float)width / 2, (((float)height / maxNumWords) * i) + ((float)height / (maxNumWords * 2))));
		}
		
		availableColors = new ArrayBlockingQueue<Integer>(maxNumWords);
		for (int color : colorsSpringHappiness) {
			availableColors.add(color);
		}
		assert availableColors.size() == maxNumWords;
		
		wordBuilderThreads = new LinkedList<Thread>();
		deadThreads = new LinkedList<Thread>();
	}
	
	public void draw() {
		background(defaultBackgroundColor);
		
		manageBuilders();
		
		// Grab any new words from the queue
		WordState tmpState;
		while (activeWords.size() < maxNumWords && (tmpState = wordsQueue.poll()) != null) {
			tmpState.created = System.currentTimeMillis();
			tmpState.location = availableLocations.poll();
			tmpState.color = availableColors.poll();
			activeWords.add(tmpState);
		}
		
		// Remove aged words and draw active words
		retiredWords.clear();
		double calcBuffer;
		int motionCoefficient;
		long currentTime = System.currentTimeMillis();
		float rotAngle;
		float horzFitFactor, vertFitFactor;
		for (WordState word : activeWords) {
			motionCoefficient = word.metaWord.length() * MOTION_FACTOR;
			
			calcBuffer = ((double)currentTime - (double)word.created) / (double)word.maxAgeInMillis;
			calcBuffer = Math.pow(2d * calcBuffer - 1d, (motionCoefficient % 2 == 1) ? motionCoefficient : motionCoefficient + 1);
			calcBuffer += 1d;
			calcBuffer /= 2d;

			rotAngle = -ROT_LIMIT + (float)calcBuffer * (ROT_LIMIT * 2);
			
			if (System.currentTimeMillis() - word.created > word.maxAgeInMillis) {
				float newAlpha = alpha(word.color) - defaultFadeOutIncrement;
				word.color = color(red(word.color), green(word.color), blue(word.color), (newAlpha<=1f)?1:newAlpha);
				if (newAlpha <= 1) {
					retiredWords.add(word);
				}
			} else if (alpha(word.color) < 255) {
				word.color = color(red(word.color), green(word.color), blue(word.color), alpha(word.color) + defaultFadeInIncrement);
			}
			
			horzFitFactor = (float)width / word.dimensions.x;
			vertFitFactor = ((float)height / (float)maxNumWords) / word.dimensions.y;
			
			pushMatrix();
			{
				translate(word.location.x, word.location.y);
				scale((horzFitFactor < vertFitFactor) ? horzFitFactor : vertFitFactor);
//				translate(0, 0, -word.dimensions.x / 2);
				rotateY(rotAngle);
//				translate(0, 0, word.dimensions.x / 2);
				translate(-word.center.x, -word.center.y);
				
				// Draw the character base polygons
				fill(word.color);
				for (int polyIndex = 0; polyIndex < word.polys.length; ++polyIndex) {
					pushMatrix();
					{
						translate(0, 0, (rotAngle==0)?0:-(word.offsets[polyIndex] * (rotAngle / ROT_LIMIT)));
						word.polys[polyIndex].draw(this);
					}
					popMatrix();
				}
			}
			popMatrix();
		}
		for (WordState retiredState : retiredWords) {
			availableLocations.add(retiredState.location);
			availableColors.add(retiredState.color);
			activeWords.remove(retiredState);
		}
	}
	
	public void findWords(String directoryPath, ArrayList<File> fileList) {
		File directory = new File(directoryPath);
		assert directory.exists() && directory.isDirectory() : "Can't find word data in " + directoryPath + "!";
		for (File file : directory.listFiles()) {
			if (file.isFile()) fileList.add(file);
			else if (file.isDirectory()) findWords(file.getPath(), fileList);
		}
	}
	
	public void manageBuilders() {
		// Ask for more words to be built
		Thread builderThread;
		for (int i = wordBuilderThreads.size(); wordsQueue.remainingCapacity() > 0 && i < maxNumWords; ++i) {
			builderThread = new Thread(new RandomWordBuilder(this.availableWords));
			wordBuilderThreads.add(builderThread);
			builderThread.start();
		}
		
		// Remove builder threads that have finished
		deadThreads.clear();
		for (Thread t : wordBuilderThreads) {
			if (!t.isAlive()) deadThreads.add(t);
		}
		for (Thread t : deadThreads) wordBuilderThreads.remove(t);
	}
	
	class RandomWordBuilder implements Runnable {
		private static final int MASK_FONTSIZE = 144;
		private static final int BASE_FONTSIZE = 36;
		private static final int MAX_WORD_LENGTH = 90;
		
		private static final String FORBIDDEN_CHARACTERS = "_ ";
		
		ArrayList<File> availableWords;
		
		RFont maskFont;
		RFont baseFont;
		
		RGroup maskText;
		RPolygon maskTextPoly;
		RPolygon maskTextStencil;
		RPolygon maskCharPoly;
		
		RPolygon baseCharPoly;
		float baseMatrixSpacing;
		
		public RandomWordBuilder(ArrayList<File> availableWords) {
			this.availableWords = availableWords;
			
//			maskFont = new RFont(APP, "ariblk.TTF", MASK_FONTSIZE, RFont.CENTER);
//			baseFont = new RFont(APP, "ariblk.TTF", BASE_FONTSIZE, RFont.CENTER);

			maskFont = new RFont(APP, "HelveticaNeueLTStd-Blk.ttf", MASK_FONTSIZE, RFont.CENTER);
			baseFont = new RFont(APP, "HelveticaNeueLTStd-BlkCnO.ttf", BASE_FONTSIZE, RFont.CENTER);
			
			baseMatrixSpacing = 2f;
		}
		
		@Override
		public void run() {
			String[] wordData;
			try {
				wordData = readRandomWordData();
			} catch (IOException e) {
				wordData = new String[] {"words", "space"};
			}
			
			try {
				wordsQueue.add(buildWordState(wordData));
			} catch (IllegalStateException e) {}
		}
		
		public WordState buildWordState(String[] wordData) {
			println("Building polys for words: " + wordData[0] + ", " + wordData[1]);
			
			List<RPolygon> polys = new LinkedList<RPolygon>();
			
			char[] baseWordChars = new char[wordData[1].length()];
			wordData[1].getChars(0, wordData[1].length(), baseWordChars, 0);
			int[] dims;
			
			RGroup baseMatrixGroup;
			RPolygon baseMatrixPoly;
			
			RPolygon tmpPoly;
			RMatrix tmpMtx;
			
			// Construct filler for each character of mask ("big") word
			int charIndex = 0;
			maskText = maskFont.toGroup(wordData[0]);
			for (RGeomElem maskChar : maskText.elements) {
				maskCharPoly = maskChar.toPolygon();
				baseCharPoly = baseFont.toPolygon(baseWordChars[charIndex]);
				
				// Construct group of charcter polygons for base character matrices
				baseMatrixGroup = new RGroup();
				dims = getBaseMatrixDims(maskCharPoly, baseCharPoly, baseMatrixSpacing);
				for (int i = 0; i < dims[0]; ++i) {
					for (int j = 0; j < dims[1]; ++j) {						
						tmpPoly = new RPolygon(baseCharPoly);
						tmpPoly.transform(getBaseMatrixOffsetMatrix(tmpPoly, i, j, baseMatrixSpacing));

						baseMatrixGroup.addElement(tmpPoly);
					}
				}
				
				// Merge individual character polys into cohesive matrix poly, and clip
				baseMatrixPoly = baseMatrixGroup.toPolygon();
				
				tmpMtx = getAlignmentMatrix(baseMatrixPoly, maskCharPoly);
				for (RGeomElem poly : baseMatrixGroup.elements) {
					((RPolygon) poly).transform(tmpMtx);
					poly = ((RPolygon) poly).intersection(maskCharPoly).update();
					if (((RPolygon) poly).contours != null) {
						polys.add((RPolygon)poly);
					}
				}
				++charIndex;
			}
			
			// Add poly that can be used as bounding box as last entry in poly array
			maskTextPoly = maskText.toPolygon();
			maskTextStencil = maskText.getBounds().toPolygon();
			
			tmpMtx = new RMatrix();
			maskTextStencil.transform(tmpMtx);
			maskTextStencil = maskTextStencil.diff(maskTextPoly);
			
			WordState state = new WordState();
			
			state.metaWord = wordData[0];
			state.baseWord = wordData[1];
			state.age = 0;
			state.maxAgeInMillis = wordData[0].length() * LIFETIME_COEFFICIENT * 1000;
//			state.color = defaultBaseColor;
			state.center = maskTextStencil.getCenter();
			state.stencil = maskTextStencil;
			state.polys = polys.toArray(new RPolygon[polys.size()]);
			state.dimensions = getContourDims(state.stencil.getBounds());
			state.offsets = new float[state.polys.length];
			for (int i = 0; i < state.offsets.length; ++i) {
				state.offsets[i] = (RANDOM.nextFloat() * state.dimensions.x) - (state.dimensions.x / 2);
			}
			
			return state;
		}
		
		/**
		 * @return Array of two strings, where the first is a random word in the database, and the second is an antonym of that word
		 * @throws IOException 
		 * @throws Throwable
		 */
		public String[] readRandomWordData() throws IOException {
			ArrayList<String> antonyms = new ArrayList<String>();
			String tmpAntonym = null;
			
			File wordFile = null;
			boolean valid = false;
			while (!valid) {
				wordFile = availableWords.get(RANDOM.nextInt(availableWords.size() - 1));
				while (wordFile.getName().length() > MAX_WORD_LENGTH || wordFile.getName().matches("[^" + FORBIDDEN_CHARACTERS + "]*[" + FORBIDDEN_CHARACTERS + "]+.*")) {
					println("Woops! '" + wordFile.getName() + "' contains one of these characters: '" + FORBIDDEN_CHARACTERS + "'. Skipping...");
					wordFile = availableWords.get(RANDOM.nextInt(availableWords.size() - 1));
				}
				BufferedReader reader = new BufferedReader(new FileReader(wordFile));
				antonyms.clear();
				while ((tmpAntonym = reader.readLine()) != null) {
					if (tmpAntonym.matches("[^" + FORBIDDEN_CHARACTERS + "]*[" + FORBIDDEN_CHARACTERS + "]+.*")) {
						println("Woops! '" + wordFile.getName() + "' antonym '" + tmpAntonym + "' contains one of these characters: '" + FORBIDDEN_CHARACTERS + "'. Skipping...");
						continue;
					}
					antonyms.add(tmpAntonym);
				}
				if (!antonyms.isEmpty()) {
					tmpAntonym = antonyms.get((antonyms.size() > 1) ? RANDOM.nextInt(antonyms.size() - 1) : 0);
					valid = true;
				}
			}
			
			return new String[] { wordFile.getName(), tmpAntonym.toUpperCase() };
		}
		
		/**
		 * @param from
		 * @param to
		 * @return Translation matrix to accomplish lining up the 'from' polygon to the 'to' polygon
		 */
		public RMatrix getAlignmentMatrix(RPolygon from, RPolygon to) {
			RPoint fromCenter = from.getCenter(), toCenter = to.getCenter();
			
			RMatrix alignMtx = new RMatrix();
			alignMtx.translate(toCenter.x - fromCenter.x, toCenter.y - fromCenter.y);
			
			return alignMtx;
		}
		
		/**
		 * @param baseChar
		 * @param col
		 * @param row
		 * @param spacing
		 * @return Translation matrix to accomplish positioning 'baseChar' polygon at specified 'col' and 'row' in a grid with 'spacing'
		 */
		public RMatrix getBaseMatrixOffsetMatrix(RPolygon baseChar, int col, int row, float spacing) {
			RPoint dims = getContourDims(baseChar.getBounds());
			RMatrix offsetMtx = new RMatrix();
			offsetMtx.translate((float)col * (dims.x + spacing), (float)row * (dims.y + spacing));
			return offsetMtx;
		}
		
		/**
		 * @param maskChar
		 * @param baseChar
		 * @param spacing
		 * @return Array where first element is number of columns of 'baseChar's that fit in 'maskChar', and second element is corresponding number of rows
		 */
		public int[] getBaseMatrixDims(RPolygon maskChar, RPolygon baseChar, float spacing) {
			float maskHeight, maskWidth, baseHeight, baseWidth;
			RPoint dims;
			
			dims = getContourDims(maskChar.getBounds());
			maskWidth = dims.x;
			maskHeight = dims.y;
			
			dims = getContourDims(baseChar.getBounds());
			baseWidth = dims.x;
			baseHeight = dims.y;
			
			return new int[] {
				ceil(maskWidth / (baseWidth + spacing)),
				ceil(maskHeight / (baseHeight + spacing))
			};
		}
	}
	
	private class WordState {
		String baseWord;
		String metaWord;
		float age;
		long created;
		long maxAgeInMillis;
		int color;
		RPoint center;
		RPoint dimensions;
		RPoint location;
		float[] offsets;
		RPolygon[] polys;
		RPolygon stencil;
	}
	
	/**
	 * @param contour
	 * @return RPoint representing width (x) and height (y) of the given 'contour'
	 */
	public static RPoint getContourDims(RContour contour) {
		float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
		float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
		
		for (RPoint point : contour.points) {
			minY = (point.y < minY) ? point.y : minY;
			maxY = (point.y > maxY) ? point.y : maxY;
		}
		
		for (RPoint point : contour.points) {
			minX = (point.x < minX) ? point.x : minX;
			maxX = (point.x > maxX) ? point.x : maxX;
		}
		
		return new RPoint(maxX - minX, maxY - minY);
	}
}

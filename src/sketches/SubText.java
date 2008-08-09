package sketches;

import processing.core.PApplet;
import processing.core.PFont;

import geomerative.*;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;

import java.util.concurrent.LinkedBlockingQueue;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;

public class SubText extends PApplet {

	private static final int CANVAS_WIDTH = 800;
	private static final int CANVAS_HEIGHT = 600;
	private static final int FRAME_RATE = 30;
	
	private final PApplet APP = this;
	private static final PApplet HELPER_APP = new PApplet();
	private static final Random RANDOM = new Random();
	
	private static final int MAX_WORDS = 5;
	private static final short LEFT = -1;
	private static final short RIGHT = 1;
	
	private static final class COLORS {
		public static final int black = HELPER_APP.color(0, 0, 0);
		public static final int white = HELPER_APP.color(255, 255, 255);
		public static final int softBlue = HELPER_APP.color(42, 69, 71);
		public static final int lightTan = HELPER_APP.color(201, 193, 165);
	}
	
	private static final class FONTS {
		public static final String liberationMonoBoldTTF = "LiberationMono-Bold.ttf";
		public static final String liberationMonoBoldVLW = "LiberationMono-Bold-128.vlw";
		public static final String liberationMonoBoldName = "Liberation Mono Bold";
	}
	
	private int backgroundColor;
	private int fillColor;
	
	public PFont subFont;
	public int maxSubFontSize = CANVAS_HEIGHT / 24;
	public int minSubFontSize = CANVAS_HEIGHT / 48;
	public int fontSize = minSubFontSize;
	public float fontWidthRatio = 0.75f;
	
	private ArrayList<File> antonymFiles = new ArrayList<File>();	
	public LinkedBlockingQueue<Word> activeWords = new LinkedBlockingQueue<Word>(MAX_WORDS);
	
	private void resetBackground() {
		background(red(this.backgroundColor), green(this.backgroundColor), blue(this.backgroundColor));
	}
	
	private void resetFill() {
		fill(red(this.fillColor), green(this.fillColor), blue(this.fillColor));
	}
	
	public void setup() {
		size(CANVAS_WIDTH, CANVAS_HEIGHT, OPENGL);
		frameRate(FRAME_RATE);
		
		subFont = loadFont(FONTS.liberationMonoBoldVLW);
		textAlign(LEFT, TOP);

		this.backgroundColor = COLORS.white;
		this.fillColor = COLORS.black;
		
		resetBackground();
		resetFill();
		
		RCommand.setSegmentator(RCommand.UNIFORMSTEP);
		RCommand.setSegmentStep(3);
				
		getAllFilesUnderDirectory("data/congruent_antonyms", this.antonymFiles);
	}

	public void draw() {
		resetBackground();
		resetFill();
		try {
			MarqueeWord majorWord;
			short direction = LEFT;
			while (activeWords.remainingCapacity() > 0) {
				int randomFontSize = RANDOM.nextInt(maxSubFontSize - minSubFontSize) + minSubFontSize;
				
				File wordFile = this.getRandomWordFile();
				String majorWordText = wordFile.getName().toUpperCase();
				
				majorWord = new AsciiMarqueeWord(majorWordText, randomFontSize, this.getSpeedFromFontSize(randomFontSize));
				majorWord.initialize(direction);
				
				String minorWordText;
				BufferedReader wordReader = null;
				try {
					wordReader = new BufferedReader(new FileReader(wordFile));
					AsciiMarqueeWord minorWord = null;
					while ((minorWordText = wordReader.readLine()) != null) {
						minorWordText = minorWordText.toUpperCase();
						minorWord = new AsciiMarqueeWord(minorWordText, randomFontSize, this.getSpeedFromFontSize(randomFontSize));
						minorWord.initialize(direction);
						
						minorWord.addSubWord(majorWord);
						majorWord.addSubWord(minorWord);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				this.activeWords.add(majorWord);
				
				direction *= -1; // flip directions
			}
		} catch (IllegalStateException e) {}
		
		for (Word word : activeWords) {
			word.draw();
		}
	}
	
	private void getAllFilesUnderDirectory(String directoryPath, ArrayList<File> files) {
		File directory = new File(directoryPath);
		assert directory.exists() && directory.isDirectory() : "The getAllFilesUnderDirectory function must be given the name of an existing directory!";
		for (File file : directory.listFiles()) {
			if (file.isFile()) {
				files.add(file);
			} else if (file.isDirectory()) {
				getAllFilesUnderDirectory(file.getPath(), files);
			}
		}
	}
	
	private File getRandomWordFile() {
		return this.antonymFiles.get(RANDOM.nextInt(this.antonymFiles.size() - 1));
	}
	
	private double getSpeedFromFontSize(double fontSize) {
		return (fontSize * fontSize) / 64d;
	}
	
	private interface SuperFont {
		public boolean isMonospaced();
		public int getColumns();
		public int getRows();
		public double getHeight();
		public double getWidth();
		public void draw(String major, String minor);
	}
	
	private abstract class MonoFont implements SuperFont {
		private HashMap<String, boolean[][]> fontMask;
		private int columns;
		private int rows;
		
		private int fontSize;
		
		private static final char MASK_MARKER = '#';
		
		private static final String MASK_WIDTH_ERROR = "Each line in character's mask must not be longer than maximum width!";
		private static final String MASK_MISSING_ERROR = "The mask for a character is missing!";
				
		public MonoFont(BufferedReader fontMaskSource, int fontSize) {
			loadMask(fontMaskSource);
			this.fontSize = fontSize;
		}
		
		private void loadMask(BufferedReader fontMaskSource) {
			int lineCount = 0;
			String line;
			LinkedList<String> characters = new LinkedList<String>();
			int characterRow = 0;
			boolean[][] characterMatrix = null;
			this.fontMask = new HashMap<String, boolean[][]>();

			try {
				while ((line = fontMaskSource.readLine()) != null) {
					++lineCount;
					if (lineCount == 1) {
						this.columns = line.length();
					} else if (lineCount == 2) {
						this.rows = line.length();
					} else if (line.length() > 0 && characters.isEmpty()) {
						// Non-empty line + no current characters = starting new character mask
						for (int i = 0; i < line.length(); ++i) {
							characters.add(line.substring(i, i + 1));
						}
						characterMatrix = new boolean[this.rows][this.columns];
						characterRow = 0;
					} else if (!characters.isEmpty()) {
						assert line.length() <= this.columns : MASK_WIDTH_ERROR + " on line: " + lineCount;
						
						// Scan each character mask's line for 'on/off' locations
						for (int column = 0; column < line.length(); ++column) {
							boolean cellValue;
							if (line.charAt(column) == MASK_MARKER) cellValue = true;
							else cellValue = false;
							characterMatrix[characterRow][column] = cellValue;
						}
						++characterRow;
						
						if (characterRow >= this.rows) {
							// Save last characters' matrix
							if (characterMatrix != null) {
								for (String character : characters) {
									fontMask.put(character, characterMatrix);
								}
							}
							
							// Reset for next character mask
							characters.clear();
							characterMatrix = null;
						}
					}
				}
				if (!characters.isEmpty()) {
					// Save left-over matrix, if any
					if (characterMatrix != null) {
						for (String character : characters) {
							fontMask.put(character, characterMatrix);
						}
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		public boolean isMonospaced() { return true; }
		
		public int getColumns() { return this.columns; }
		
		public int getRows() { return this.rows; }
		
		public double getWidth() {
			return this.columns * this.fontSize * fontWidthRatio;
		}
		
		public double getHeight() {
			return this.rows * this.fontSize;
		}
		
		public double getColumnOffset(int column) {
			return column * this.fontSize * fontWidthRatio;
		}
		
		public double getRowOffset(int row) {
			return row * this.fontSize;
		}
		
		public void draw(String major, String minor) {
			boolean[][] characterMatrix = fontMask.get(major);
			assert characterMatrix != null : MASK_MISSING_ERROR + " character: " + major;
			for (int row = 0; row < this.rows; ++row) {
				textFont(subFont, this.fontSize);
				for (int column = 0; column < this.columns; ++column) {
					if (characterMatrix[row][column]) {
						text(minor, (int)this.getColumnOffset(column), (int)this.getRowOffset(row));
					}
				}
			}
		}
	}
	
	private class BannerFont extends MonoFont {
		private static final String MASK_FILE = "data/banner_font_mask.txt";
		
		public BannerFont(int fontSize) throws FileNotFoundException {
			super(new BufferedReader(new FileReader(MASK_FILE)), fontSize);
		}
	}
	
	private interface Word {
		public String getText();
		public void addSubWord(Word subWord);
		public Word nextSubWord();
		public long length();
		public void draw();
	}
	
	private class MarqueeWord implements Word {
		private String text = null;
		protected LinkedBlockingQueue<Word> subWords = new LinkedBlockingQueue<Word>();

		protected int fontSize;
		
		private short direction = LEFT;
		private double speed = 2.0;
		private double x = 0, y = 0;
		
		protected static final String SUB_WORD_NULL_MSG = "Word must have a non-null subWord";
		protected static final String LENGTH_ERROR_MSG = "Sub-word must be the same length as word";
		protected static final String DIRECTION_ERROR_MSG = "Direction must be LEFT or RIGHT";
		protected static final String FONT_ERROR_MSG = "Font-mask file must exist!";
		
		public MarqueeWord(String text, int fontSize) {
			this.fontSize = fontSize;
			this.setText(text);
		}
		
		public MarqueeWord(String text, int fontSize, Word subWord) {
			this(text, fontSize);
			assert this.text.length() == subWord.length() : LENGTH_ERROR_MSG;
			this.addSubWord(subWord);
		}
		
		public MarqueeWord(String text, int fontSize, double speed) {
			this(text, fontSize);
			this.setSpeed(speed);
		}
		
		public MarqueeWord(String text, int fontSize, Word subWord, double speed) {
			this(text, fontSize, subWord);
			this.setSpeed(speed);
		}
		
		public void initialize(short direction) {
			this.setDirection(direction);
			if (direction == LEFT) {
				this.setX(width);
			} else if (direction == RIGHT) {
				this.setX(-this.getWidth());
			}
			this.setY(((double)height) * random(0f, (float)(height - this.getHeight()) / height));
		}
		
		public void draw() {};
		
		public double getWidth() {
			return -1d;
		}
		
		public double getHeight() {
			return -1d;
		}
		
		public long length() {
			return this.text.length();
		}
		
		public double getSpeed() {
			return this.speed;
		}
		
		public void setSpeed(double speed) {
			this.speed = speed;
		}
		
		public short getDirection() {
			return this.direction;
		}
		
		public void setDirection(short direction) {
			assert direction == LEFT || direction == RIGHT : DIRECTION_ERROR_MSG;
			this.direction = direction;
		}
		
		public double getX() {
			return this.x;
		}
		
		public void setX(double x) {
			this.x = x;
		}
		
		public double getY() {
			return this.y;
		}
		
		public void setY(double y) {
			this.y = y;
		}
		
		public String getText() {
			return this.text;
		}
		
		public void setText(String text) {
			if (subWords.peek() != null) assert text.length() == subWords.peek().length() : LENGTH_ERROR_MSG;
			this.text = text;
		}
		
		public void addSubWord(Word subWord) {
			assert this.text.length() == subWord.length() : LENGTH_ERROR_MSG;
			subWords.add(subWord);
		}
		
		public Word nextSubWord() {
			if (this.subWords.size() > 1) {
				// Move head to tail, then return moved word
				Word nextSubWord = subWords.remove();
				subWords.add(nextSubWord);
				return nextSubWord;
			}
			
			// 1 queue entry = don't bother with head -> tail move
			return subWords.peek();
		}
	}
	
	private class AsciiMarqueeWord extends MarqueeWord implements Word {
		private SuperFont wordFont;
		
		public AsciiMarqueeWord(String text, int fontSize) {
			super(text, fontSize);
			try {
				this.wordFont = new BannerFont(this.fontSize);
			} catch (FileNotFoundException e) {
				throw new AssertionError(FONT_ERROR_MSG);
			}
		}
		
		public AsciiMarqueeWord(String text, int fontSize, double speed) {
			this(text, fontSize);
			this.setSpeed(speed);
		}
		
		public double getWidth() {
			return this.length() * (this.wordFont.getColumns() + 1) * fontWidthRatio * this.fontSize;
		}
		
		public double getHeight() {
			return this.wordFont.getHeight();
		}
		
		public double getCharacterOffset(int index) {
			return index * (this.wordFont.getColumns() + 1) * fontWidthRatio * this.fontSize;
		}
		
		public boolean isVisible() {
			if (this.getX() > width || this.getX() < -this.getWidth()) {
				return false;
			}
			return true;
		}
		
		public void draw() {
			assert !this.subWords.isEmpty() : SUB_WORD_NULL_MSG;
			if (!this.isVisible()) {
				// Reset position and don't draw
				this.initialize(this.getDirection());
				
				// Swap out for next subWord in line
				activeWords.remove(this);
				activeWords.add(this.nextSubWord());
			} else {
				// Adjust position accordingly
				this.setX(this.getX() + this.getSpeed() * this.getDirection());
				
				// ...and draw each character
				String subWordText = this.subWords.peek().getText();
				for (int i = 0; i < this.getText().length(); ++i) {
					pushMatrix();
					{
						translate((float)(this.getX() + getCharacterOffset(i)), (float)this.getY());
						String majorCharacter = this.getText().substring(i, i + 1);
						String minorCharacter = subWordText.substring(i, i + 1);
						wordFont.draw(majorCharacter, minorCharacter);
					}
					popMatrix();
				}
			}
		}
	}

	private class OutlineMarqueeWord extends MarqueeWord implements Word {
		private float FONT_SIZE_MULTIPLIER = 10f;
		private float OUTLINE_STRING_SPACE_FACTOR = 13f;
		
		private RFont wordFont, wordSubFont;
		RGroup wordCharactersShapes = null;
		
		public OutlineMarqueeWord(String text, int fontSize) {
			super(text, fontSize);
			this.wordFont = new RFont(APP,  "LiberationMono-Bold.ttf", this.getMasterFontSize(), RFont.LEFT);
			this.wordSubFont = new RFont(APP, "LiberationMono-Bold.ttf", fontSize, RFont.LEFT);
			this.wordCharactersShapes = wordFont.toGroup(text);
		}
		
		public OutlineMarqueeWord(String text, int fontSize, double speed) {
			this(text, fontSize);
			this.setSpeed(speed);
		}
		
		public int getMasterFontSize() {
			return (int)(this.fontSize * this.FONT_SIZE_MULTIPLIER);
		}
		
		public boolean isVisible() {
			RShape characterShape;
			for (RGeomElem character : wordCharactersShapes.elements) {
				characterShape = (RShape)character;
				if (characterShape.isIn(g)) return true;
			}
			return false;
		}
		
		public double getCharacterOffset(int i) {
			if (i == 0) return 0;
			double offset = 0;
			RShape precedingCharacter;
			RContour bounds;
			double minX, maxX;
			for (int j = 0; j < i; ++j) {
				precedingCharacter = (RShape)this.wordCharactersShapes.elements[j];
				bounds = precedingCharacter.getBounds();
				minX = Double.MAX_VALUE;
				maxX = Double.MIN_VALUE;
				for (RPoint boundPoint : bounds.points) {
					minX = min((float)minX, boundPoint.x);
					maxX = max((float)maxX, boundPoint.x);
				}
				offset += maxX - minX;
			}
			return offset;
		}
		
		public double getWidth() {
			double width = 0;
			RContour bounds;
			double minX, maxX;
			for (int i = 0; i < this.wordCharactersShapes.elements.length; ++i) {
				bounds = ((RShape)this.wordCharactersShapes.elements[i]).getBounds();
				minX = Double.MAX_VALUE;
				maxX = Double.MIN_VALUE;
				for (RPoint boundPoint : bounds.points) {
					minX = min((float)minX, boundPoint.x);
					maxX = max((float)maxX, boundPoint.x);
				}
				width += maxX - minX;
			}
			return width;
		}
		
		public double getHeight() {
			return 0;
		}
		
		public void draw() {
			assert !this.subWords.isEmpty() : SUB_WORD_NULL_MSG;
			if (!this.isVisible()) {
				// Reset position and don't draw
				this.initialize(this.getDirection());
				
				// Swap out for next subWord in line
				activeWords.remove(this);
				activeWords.add(this.nextSubWord());
			} else {
				// Adjust position accordingly
				this.setX(this.getX() + this.getSpeed() * this.getDirection());
				
				// ...and draw each character...
				String subWordText = this.subWords.peek().getText();
				char subWordCharacter;
				char[] outlineCharacters;
				int outlineStringLength;
				RGroup outlineString;
				RShape wordCharacter;
				double offsetX;
				RMatrix trans;
				
				// for-each character in word...
				for (int i = 0; i < this.wordCharactersShapes.elements.length; ++i) {
					wordCharacter = (RShape)this.wordCharactersShapes.elements[i];
					subWordCharacter = subWordText.charAt(i);
					pushMatrix();
					{
						// position it...
						offsetX = this.getX() + getCharacterOffset(i);
						trans = new RMatrix();
						trans.translate((float)offsetX, (float)this.getY());
						
						// and use sub-word character to draw outline of each sub-shape in main word's character
						for (RSubshape subShape : ((RShape)wordCharacter).subshapes) {

							// get proper sub-word character string for outlining...
							outlineStringLength = floor(subShape.getCurveLength() / this.fontSize);
							outlineCharacters = new char[outlineStringLength];
							for (int k = 0; k < outlineStringLength; ++k) {
								outlineCharacters[k] = subWordCharacter;
							}
							outlineString = wordSubFont.toGroup(new String(outlineCharacters));
							outlineString = outlineString.adaptTo(subShape);
							outlineString.transform(trans);
							outlineString.draw(g);
						}
					}
					popMatrix();
				}
			}
		}
	}
	
	// Serializable interface cruft that I guess Processing takes care of...
	static final long serialVersionUID = 1337L;	
}
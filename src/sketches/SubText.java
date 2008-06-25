package sketches;

import processing.core.PApplet;
import processing.core.PFont;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;

import java.util.concurrent.ArrayBlockingQueue;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;

public class SubText extends PApplet {
	
	private static final int MAX_WORDS = 5;
	private static final short LEFT = -1;
	private static final short RIGHT = 1;

	private static final int CANVAS_WIDTH = 640;
	private static final int CANVAS_HEIGHT = 480;
	
	private final int softBlue = (new PApplet()).color(42, 69, 71);
	private final int lightTan = (new PApplet()).color(201, 193, 165);
	
	private int backgroundColor;
	private int fillColor;
	
	private ArrayList<File> antonymFiles = new ArrayList<File>();
	private Random generator = new Random();
	
	public PFont subFont;
	
	public int maxSubFontSize = CANVAS_HEIGHT / 32;
	public int minSubFontSize = CANVAS_HEIGHT / 96;
	public int fontSize = minSubFontSize;
	public float fontWidthRatio = 0.75f;
	
	public ArrayBlockingQueue<Word> words = new ArrayBlockingQueue<Word>(MAX_WORDS);
	
	private void resetBackground() {
		background(red(this.backgroundColor), green(this.backgroundColor), blue(this.backgroundColor));
	}
	
	private void resetFill() {
		fill(red(this.fillColor), green(this.fillColor), blue(this.fillColor));
	}
	
	public void setup() {
		this.backgroundColor = this.lightTan;
		this.fillColor = this.softBlue;
		
		getAllFilesUnderDirectory("data/congruent_antonyms", this.antonymFiles);
		
		size(CANVAS_WIDTH, CANVAS_HEIGHT, OPENGL);
		frameRate(30);
		
		resetBackground();
		resetFill();
		
		subFont = loadFont("LiberationMono-Bold-128.vlw");
		textAlign(LEFT, TOP);
	}

	public void draw() {
		resetBackground();
		resetFill();
		try {
			MarqueeWord word1, word2;
			short direction = LEFT;
			while (true) {
				File wordFile = getRandomWordFile();
				String majorWord = wordFile.getName().toUpperCase();
				String minorWord = majorWord;
				try {
					minorWord = (new BufferedReader(new FileReader(wordFile))).readLine().toUpperCase();
				} catch (IOException e) {
					e.printStackTrace();
				}
				int randomFontSize = generator.nextInt(maxSubFontSize - minSubFontSize) + minSubFontSize;
				
				word1 = new MarqueeWord(majorWord, randomFontSize, (randomFontSize * randomFontSize) / (32));
				word2 = new MarqueeWord(minorWord, randomFontSize, (randomFontSize * randomFontSize) / (32));
				word1.setSubWord(word2);
				word2.setSubWord(word1);

				word1.setDirection(direction);
				word2.setDirection(direction);
				if (direction == LEFT) {
					word1.setX(this.width);
					word1.setY(((double)this.height) * random(0f, (float)(height - word1.getHeight()) / height));
					word2.setX(this.width);
					word2.setY(((double)this.height) * random(0f, (float)(height - word1.getHeight()) / height));
				} else if (direction == RIGHT) {
					word1.setX(-word1.getWidth());
					word1.setY(((double)this.height) * random(0f, (float)(height - word1.getHeight()) / height));
					word2.setX(-word1.getWidth());
					word2.setY(((double)this.height) * random(0f, (float)(height - word1.getHeight()) / height));
				}
				this.words.add(word1);
				
				//direction *= -1;
			}
		} catch (IllegalStateException e) {}
		for (Word word : words) {
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
		return this.antonymFiles.get(generator.nextInt(this.antonymFiles.size() - 1));
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
		public void setSubWord(Word subWord);
		public Word getSubWord();
		public long length();
		public void draw();
	}
	
	private class MarqueeWord implements Word {
		private String text = null;
		private Word subWord = null;
		
		private SuperFont wordFont;
		private int fontSize;
		
		private short direction = LEFT;
		private double speed = 2.0;
		private double x = 0, y = 0;
		
		private static final String SUB_WORD_NULL_MSG = "Word must have a non-null subWord";
		private static final String LENGTH_ERROR_MSG = "Sub-word must be the same length as word";
		private static final String DIRECTION_ERROR_MSG = "Direction must be LEFT or RIGHT";
		private static final String FONT_ERROR_MSG = "Font-mask file must exist!";
		
		public MarqueeWord(String text) {
			this(text, -1);
		}
		
		public MarqueeWord(String text, int fontSize) {
			this.fontSize = fontSize;
			try {
				this.wordFont = new BannerFont(this.fontSize);
			} catch (FileNotFoundException e) {
				throw new AssertionError(FONT_ERROR_MSG);
			}
			this.setText(text);
		}
		
		public MarqueeWord(String text, int fontSize, Word subWord) {
			this(text, fontSize);
			assert this.text.length() == subWord.length() : LENGTH_ERROR_MSG;
			this.setSubWord(subWord);
		}
		
		public MarqueeWord(String text, int fontSize, double speed) {
			this(text, fontSize);
			this.setSpeed(speed);
		}
		
		public MarqueeWord(String text, int fontSize, Word subWord, double speed) {
			this(text, fontSize, subWord);
			this.setSpeed(speed);
		}
		
		public long length() {
			return this.text.length();
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
		
		public void draw() {
			assert this.subWord != null : SUB_WORD_NULL_MSG;
			if (!this.isVisible()) {
				// Reset position and don't draw
				if (this.getDirection() == LEFT) {
					this.setX(width);
				} else if (this.getDirection() == RIGHT) {
					this.setX(-this.getWidth());
				}
				this.setY(height * random(0f, (float)(height - this.getHeight()) / height));
				
				// swap major word
				words.remove(this);
				words.add(this.subWord);
			} else {
				// Adjust position accordingly
				this.setX(this.getX() + this.getSpeed() * this.getDirection());
				
				// ...and draw each character
				for (int i = 0; i < this.getText().length(); ++i) {
					pushMatrix();
					{
						translate((float)(this.getX() + getCharacterOffset(i)), (float)this.getY());
						String majorCharacter = this.getText().substring(i, i + 1);
						String minorCharacter = this.getSubWord().getText().substring(i, i + 1);
						wordFont.draw(majorCharacter, minorCharacter);
					}
					popMatrix();
				}
			}
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
			if (subWord != null) assert text.length() == subWord.length() : LENGTH_ERROR_MSG;
			this.text = text;
		}
		
		public void setSubWord(Word subWord) {
			assert this.text.length() == subWord.length() : LENGTH_ERROR_MSG;
			this.subWord = subWord;
		}
		
		public Word getSubWord() {
			return this.subWord;
		}
		
		public boolean isVisible() {
			if (this.getX() > width || this.getX() < -this.getWidth()) {
				return false;
			}
			return true;
		}
	}

	// Serializable interface cruft that I guess Processing takes care of...
	static final long serialVersionUID = 1337L;	
}

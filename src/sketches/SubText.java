package sketches;

import processing.core.PApplet;
import processing.core.PFont;

import java.util.HashMap;
import java.util.LinkedList;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;

public class SubText extends PApplet {
	
	private static final int MAX_WORDS = 2;
	private static final short LEFT = -1;
	private static final short RIGHT = 1;

	private static final int CANVAS_WIDTH = 600;
	private static final int CANVAS_HEIGHT = 800;
	
	private final int softBlue = (new PApplet()).color(42, 69, 71);
	private final int lightTan = (new PApplet()).color(201, 193, 165);
	
	private int backgroundColor;
	private int fillColor;
	
	public PFont subFont;
	
	public int maxSubFontSize = CANVAS_HEIGHT / 18;
	public int minSubFontSize = CANVAS_HEIGHT / 36;
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
			MarqueeWord word;
			while (true) {
				word = new MarqueeWord("pro", fontSize, new MarqueeWord("CON"), fontSize / 4.0d);
				word.setDirection(LEFT);
				word.setX(this.width);
				word.setY(((double)this.height) * 0.66);
				words.add(word);

				word = new MarqueeWord("CON", fontSize / 2, new MarqueeWord("pro"), fontSize / 12.0d);
				word.setDirection(RIGHT);
				word.setX(-word.getWidth());
				word.setY(((double)this.height) * 0.33);
				words.add(word);
			}
		} catch (IllegalStateException e) {}
		for (Word word : words) {
			word.draw();
		}
	}
	
	private interface SuperFont {
		public boolean isMonospaced();
		public int getWidth();
		public int getHeight();
		public void draw(String major, String minor);
	}
	
	private abstract class MonoFont implements SuperFont {
		private HashMap<String, boolean[][]> fontMask;
		private int width;
		private int height;
		
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
						this.width = line.length();
					} else if (lineCount == 2) {
						this.height = line.length();
					} else if (line.length() > 0 && characters.isEmpty()) {
						// Non-empty line + no current characters = starting new character mask
						for (int i = 0; i < line.length(); ++i) {
							characters.add(line.substring(i, i + 1));
						}
						characterMatrix = new boolean[this.height][this.width];
						characterRow = 0;
					} else if (!characters.isEmpty()) {
						assert line.length() <= this.width : MASK_WIDTH_ERROR + " on line: " + lineCount;
						
						// Scan each character mask's line for 'on/off' locations
						for (int column = 0; column < line.length(); ++column) {
							boolean cellValue;
							if (line.charAt(column) == MASK_MARKER) cellValue = true;
							else cellValue = false;
							characterMatrix[characterRow][column] = cellValue;
						}
						++characterRow;
						
						if (characterRow >= this.height) {
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
		
		public int getWidth() { return this.width; }
		
		public int getHeight() { return this.height; }
		
		public double getColumnOffset(int column) {
			return column * this.fontSize * fontWidthRatio;
		}
		
		public double getRowOffset(int row) {
			return row * this.fontSize;
		}
		
		public void draw(String major, String minor) {
			boolean[][] characterMatrix = fontMask.get(major);
			assert characterMatrix != null : MASK_MISSING_ERROR + " character: " + major;
			for (int row = 0; row < this.height; ++row) {
				textFont(subFont, this.fontSize);
				for (int column = 0; column < this.width; ++column) {
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
			return (this.length() * (this.wordFont.getWidth()) + 1) * fontWidthRatio * this.fontSize;
		}
		
		public double getCharacterOffset(int index) {
			return index * (this.wordFont.getWidth() + 1) * fontWidthRatio * this.fontSize;
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

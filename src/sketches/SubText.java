package sketches;

import processing.core.PApplet;
import processing.core.PFont;

import java.util.HashMap;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;

public class SubText extends PApplet {
	
	private static final int MAX_WORDS = 1;
	private static final int LEFT = -1;
	private static final int RIGHT = 1;
	
	public PFont subFont;
	
	public int maxFontSize = 96;
	public int minFontSize = 12;
	public int fontSize = minFontSize;
	public float fontWidthRatio = 0.75f;
	public ArrayBlockingQueue<Word> words = new ArrayBlockingQueue<Word>(MAX_WORDS);
	
	public void setup() {
		size(800, 800, OPENGL);
		//background(140, 134, 39);
		background(0);
		//fill(64, 60, 1);
		fill(255);
		
		subFont = loadFont("LiberationMono-Bold-128.vlw");
		textFont(subFont, fontSize);
		textAlign(LEFT, TOP);
	}

	public void draw() {
		//background(140, 134, 39);
		background(0);
		try {
			words.add(new MarqueeWord("POSITIVITY", new MarqueeWord("NEGATIVITY")));
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
		
		private static final char MASK_MARKER = '#';
		private String[] characters = { "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z" };
		
		public MonoFont(BufferedReader fontMaskSource) {
			loadMask(fontMaskSource);
		}
		
		private void loadMask(BufferedReader fontMaskSource) {
			int lineCount = 0;
			String line;
			int characterCount = -1;
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
					} else if (line.length() == 0) {
						if (characterMatrix != null) {
							fontMask.put(characters[characterCount], characterMatrix);
						}
						++characterCount;
						if (characterCount >= characters.length) break;
						characterMatrix = new boolean[this.height][this.width];
						characterRow = 0;
					} else {
						for (int column = 0; column < line.length(); ++column) {
							boolean cellValue;
							if (line.charAt(column) == MASK_MARKER) cellValue = true;
							else cellValue = false;
							characterMatrix[characterRow][column] = cellValue;
						}
						++characterRow;
					}
				}
			} catch (IOException e) {
				System.out.print(" ! I/O ERROR ! ");
			}
		}
		
		public boolean isMonospaced() { return true; }
		
		public int getWidth() { return this.width; }
		
		public int getHeight() { return this.height; }
		
		public void draw(String major, String minor) {
			boolean[][] characterMatrix = fontMask.get(major);
			for (int row = 0; row < this.height; ++row) {
				for (int column = 0; column < this.width; ++column) {
					if (characterMatrix[row][column]) {
						text(minor, (int)(column * fontSize * fontWidthRatio), row * fontSize);
					}
				}
			}
		}
	}
	
	private class BannerFont extends MonoFont {
		private static final String MASK_FILE = "data/banner_font_mask.txt";
		
		public BannerFont() throws FileNotFoundException {
			super(new BufferedReader(new FileReader(MASK_FILE)));
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
		
		private short direction = LEFT;
		private double speed = 2.0;
		private double x = width, y = 0;
		
		private static final String SUB_WORD_NULL_MSG = "Word must have a non-null subWord";
		private static final String LENGTH_ERROR_MSG = "Sub-word must be the same length as word";
		private static final String DIRECTION_ERROR_MSG = "Direction must be LEFT or RIGHT";
		private static final String FONT_ERROR_MSG = "Font-mask file must exist!";
		
		public MarqueeWord(String text) {
			try {
				this.wordFont = new BannerFont();
			} catch (FileNotFoundException e) {
				throw new AssertionError(FONT_ERROR_MSG);
			}
			this.setText(text);
		}
		
		public MarqueeWord(String text, double speed) {
			this(text);
			this.setSpeed(speed);
		}
		
		public MarqueeWord(String text, Word subWord) {
			this(text);
			assert this.text.length() == subWord.length() : LENGTH_ERROR_MSG;
			this.setSubWord(subWord);
		}
		
		public MarqueeWord(String text, Word subWord, double speed) {
			this(text, subWord);
			this.setSpeed(speed);
		}
		
		public long length() {
			return this.text.length();
		}
		
		public double getWidth() {
			return ((this.length() * this.wordFont.getWidth()) + this.length()) * fontWidthRatio * fontSize;
		}
		
		public void draw() {
			assert this.subWord != null : SUB_WORD_NULL_MSG;
			int characterCount = this.getText().length();
			for (int i = 0; i < characterCount; ++i) {
				pushMatrix();
				{
					translate((float)this.getX() + i * (this.wordFont.getWidth() + 1) * fontWidthRatio * fontSize, 0);
					String majorCharacter = this.text.substring(i, i + 1);
					String minorCharacter = this.getSubWord().getText().substring(i, i + 1);
					wordFont.draw(majorCharacter, minorCharacter);
				}
				popMatrix();
			}
			if (-this.getX() > this.getWidth() && this.getDirection() == LEFT) {
				this.setX(width);
			} else {
				this.setX(this.getX() + this.getSpeed() * this.getDirection());
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
	}

	// Serializable interface cruft that I guess Processing takes care of...
	static final long serialVersionUID = 1337L;	
}

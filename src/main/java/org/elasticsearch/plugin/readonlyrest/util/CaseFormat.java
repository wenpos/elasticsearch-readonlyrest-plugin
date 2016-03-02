package org.elasticsearch.plugin.readonlyrest.util;
import static org.elasticsearch.common.base.Preconditions.*;

import org.elasticsearch.common.base.Ascii;
import org.elasticsearch.common.base.Converter;

import java.io.Serializable;

/**
 * Created by sscarduzio on 02/03/2016.
 */
public enum CaseFormat {
  /**
   * Hyphenated variable naming convention, e.g., "lower-hyphen".
   */
  LOWER_HYPHEN(CharMatcher.is('-'), "-") {
    @Override
    String normalizeWord(String word) {
      return Ascii.toLowerCase(word);
    }

    @Override
    String convert(CaseFormat format, String s) {
      if (format == LOWER_UNDERSCORE) {
        return s.replace('-', '_');
      }
      if (format == UPPER_UNDERSCORE) {
        return Ascii.toUpperCase(s.replace('-', '_'));
      }
      return super.convert(format, s);
    }
  },

  /**
   * C++ variable naming convention, e.g., "lower_underscore".
   */
  LOWER_UNDERSCORE(CharMatcher.is('_'), "_") {
    @Override
    String normalizeWord(String word) {
      return Ascii.toLowerCase(word);
    }

    @Override
    String convert(CaseFormat format, String s) {
      if (format == LOWER_HYPHEN) {
        return s.replace('_', '-');
      }
      if (format == UPPER_UNDERSCORE) {
        return Ascii.toUpperCase(s);
      }
      return super.convert(format, s);
    }
  },

  /**
   * Java variable naming convention, e.g., "lowerCamel".
   */
  LOWER_CAMEL(CharMatcher.inRange('A', 'Z'), "") {
    @Override
    String normalizeWord(String word) {
      return firstCharOnlyToUpper(word);
    }
  },

  /**
   * Java and C++ class naming convention, e.g., "UpperCamel".
   */
  UPPER_CAMEL(CharMatcher.inRange('A', 'Z'), "") {
    @Override
    String normalizeWord(String word) {
      return firstCharOnlyToUpper(word);
    }
  },

  /**
   * Java and C++ constant naming convention, e.g., "UPPER_UNDERSCORE".
   */
  UPPER_UNDERSCORE(CharMatcher.is('_'), "_") {
    @Override
    String normalizeWord(String word) {
      return Ascii.toUpperCase(word);
    }

    @Override
    String convert(CaseFormat format, String s) {
      if (format == LOWER_HYPHEN) {
        return Ascii.toLowerCase(s.replace('_', '-'));
      }
      if (format == LOWER_UNDERSCORE) {
        return Ascii.toLowerCase(s);
      }
      return super.convert(format, s);
    }
  };

  private final CharMatcher wordBoundary;
  private final String wordSeparator;

  CaseFormat(CharMatcher wordBoundary, String wordSeparator) {
    this.wordBoundary = wordBoundary;
    this.wordSeparator = wordSeparator;
  }

  /**
   * Converts the specified {@code String str} from this format to the specified {@code format}. A
   * "best effort" approach is taken; if {@code str} does not conform to the assumed format, then
   * the behavior of this method is undefined but we make a reasonable effort at converting anyway.
   */
  public final String to(CaseFormat format, String str) {
    checkNotNull(format);
    checkNotNull(str);
    return (format == this) ? str : convert(format, str);
  }

  /**
   * Enum values can override for performance reasons.
   */
  String convert(CaseFormat format, String s) {
    // deal with camel conversion
    StringBuilder out = null;
    int i = 0;
    int j = -1;
    while ((j = wordBoundary.indexIn(s, ++j)) != -1) {
      if (i == 0) {
        // include some extra space for separators
        out = new StringBuilder(s.length() + 4 * wordSeparator.length());
        out.append(format.normalizeFirstWord(s.substring(i, j)));
      } else {
        out.append(format.normalizeWord(s.substring(i, j)));
      }
      out.append(format.wordSeparator);
      i = j + wordSeparator.length();
    }
    return (i == 0)
        ? format.normalizeFirstWord(s)
        : out.append(format.normalizeWord(s.substring(i))).toString();
  }

  /**
   * Returns a {@code Converter} that converts strings from this format to {@code targetFormat}.
   *
   * @since 16.0
   */
  public Converter<String, String> converterTo(CaseFormat targetFormat) {
    return new StringConverter(this, targetFormat);
  }

  private static final class StringConverter extends Converter<String, String>
      implements Serializable {

    private final CaseFormat sourceFormat;
    private final CaseFormat targetFormat;

    StringConverter(CaseFormat sourceFormat, CaseFormat targetFormat) {
      this.sourceFormat = checkNotNull(sourceFormat);
      this.targetFormat = checkNotNull(targetFormat);
    }

    @Override
    protected String doForward(String s) {
      return sourceFormat.to(targetFormat, s);
    }

    @Override
    protected String doBackward(String s) {
      return targetFormat.to(sourceFormat, s);
    }

    @Override
    public boolean equals( Object object) {
      if (object instanceof StringConverter) {
        StringConverter that = (StringConverter) object;
        return sourceFormat.equals(that.sourceFormat) && targetFormat.equals(that.targetFormat);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return sourceFormat.hashCode() ^ targetFormat.hashCode();
    }

    @Override
    public String toString() {
      return sourceFormat + ".converterTo(" + targetFormat + ")";
    }

    private static final long serialVersionUID = 0L;
  }

  abstract String normalizeWord(String word);

  private String normalizeFirstWord(String word) {
    return (this == LOWER_CAMEL) ? Ascii.toLowerCase(word) : normalizeWord(word);
  }

  private static String firstCharOnlyToUpper(String word) {
    return (word.isEmpty())
        ? word
        : new StringBuilder(word.length())
        .append(Ascii.toUpperCase(word.charAt(0)))
        .append(Ascii.toLowerCase(word.substring(1)))
        .toString();
  }
}
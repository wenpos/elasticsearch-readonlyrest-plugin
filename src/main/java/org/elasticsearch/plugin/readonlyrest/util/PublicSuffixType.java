package org.elasticsearch.plugin.readonlyrest.util;

/**
 * Created by sscarduzio on 02/03/2016.
 */
enum PublicSuffixType {

  /** private definition of a top-level domain */
  PRIVATE(':', ','),
  /** ICANN definition of a top-level domain */
  ICANN('!', '?');

  /** The character used for an inner node in the trie encoding */
  private final char innerNodeCode;

  /** The character used for a leaf node in the trie encoding */
  private final char leafNodeCode;

  private PublicSuffixType(char innerNodeCode, char leafNodeCode) {
    this.innerNodeCode = innerNodeCode;
    this.leafNodeCode = leafNodeCode;
  }

  char getLeafNodeCode() {
    return leafNodeCode;
  }

  char getInnerNodeCode() {
    return innerNodeCode;
  }

  /** Returns a PublicSuffixType of the right type according to the given code */
  static PublicSuffixType fromCode(char code) {
    for (PublicSuffixType value : values()) {
      if (value.getInnerNodeCode() == code || value.getLeafNodeCode() == code) {
        return value;
      }
    }
    throw new IllegalArgumentException("No enum corresponding to given code: " + code);
  }

  static PublicSuffixType fromIsPrivate(boolean isPrivate) {
    return isPrivate ? PRIVATE : ICANN;
  }
}
package org.elasticsearch.plugin.readonlyrest.util;

import org.elasticsearch.common.base.Joiner;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.common.collect.Lists;

import java.util.List;

/**
 * Created by sscarduzio on 02/03/2016.
 */
final class TrieParser {

  private static final Joiner PREFIX_JOINER = Joiner.on("");

  /**
   * Parses a serialized trie representation of a map of reversed public suffixes into an immutable
   * map of public suffixes.
   */
  static ImmutableMap<String, PublicSuffixType> parseTrie(CharSequence encoded) {
    ImmutableMap.Builder<String, PublicSuffixType> builder = ImmutableMap.builder();
    int encodedLen = encoded.length();
    int idx = 0;
    while (idx < encodedLen) {
      idx +=
          doParseTrieToBuilder(
              Lists.<CharSequence>newLinkedList(), encoded.subSequence(idx, encodedLen), builder);
    }
    return builder.build();
  }

  /**
   * Parses a trie node and returns the number of characters consumed.
   *
   * @param stack The prefixes that preceed the characters represented by this node. Each entry of
   *     the stack is in reverse order.
   * @param encoded The serialized trie.
   * @param builder A map builder to which all entries will be added.
   * @return The number of characters consumed from {@code encoded}.
   */
  private static int doParseTrieToBuilder(
      List<CharSequence> stack,
      CharSequence encoded,
      ImmutableMap.Builder<String, PublicSuffixType> builder) {

    int encodedLen = encoded.length();
    int idx = 0;
    char c = '\0';

    // Read all of the characters for this node.
    for (; idx < encodedLen; idx++) {
      c = encoded.charAt(idx);
      if (c == '&' || c == '?' || c == '!' || c == ':' || c == ',') {
        break;
      }
    }

    stack.add(0, reverse(encoded.subSequence(0, idx)));

    if (c == '!' || c == '?' || c == ':' || c == ',') {
      // '!' represents an interior node that represents an ICANN entry in the map.
      // '?' represents a leaf node, which represents an ICANN entry in map.
      // ':' represents an interior node that represents a private entry in the map
      // ',' represents a leaf node, which represents a private entry in the map.
      String domain = PREFIX_JOINER.join(stack);
      if (domain.length() > 0) {
        builder.put(domain, PublicSuffixType.fromCode(c));
      }
    }
    idx++;

    if (c != '?' && c != ',') {
      while (idx < encodedLen) {
        // Read all the children
        idx += doParseTrieToBuilder(stack, encoded.subSequence(idx, encodedLen), builder);
        if (encoded.charAt(idx) == '?' || encoded.charAt(idx) == ',') {
          // An extra '?' or ',' after a child node indicates the end of all children of this node.
          idx++;
          break;
        }
      }
    }
    stack.remove(0);
    return idx;
  }

  private static CharSequence reverse(CharSequence s) {
    return new StringBuilder(s).reverse();
  }
}
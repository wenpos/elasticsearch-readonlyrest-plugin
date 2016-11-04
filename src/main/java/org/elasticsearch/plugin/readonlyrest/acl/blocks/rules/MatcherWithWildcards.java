package org.elasticsearch.plugin.readonlyrest.acl.blocks.rules;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugin.readonlyrest.ConfigurationHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by sscarduzio on 02/04/2016.
 */
public class MatcherWithWildcards {

  private final static ESLogger logger = Loggers.getLogger(MatcherWithWildcards.class);

  protected Set<String> allMatchers = Sets.newHashSet();
  protected Set<Pattern> wildcardMatchers = Sets.newHashSet();;
  private static Set<String> empty = new HashSet<>(0);

  public Set<String> getMatchers() {
    return allMatchers;
  }

  public MatcherWithWildcards(Set<String> matchers){
    for (String a: matchers) {
      a = normalizePlusAndMinusIndex(a);
      if (ConfigurationHelper.isNullOrEmpty(a)) {
        continue;
      }
      if (a.contains("*")) {
        // Patch the simple star wildcard to become a regex: ("*" -> ".*")
        String regex = ("\\Q" + a + "\\E").replace("*", "\\E.*\\Q");

        // Pre-compile the regex pattern matcher to validate the regex
        // AND faster matching later on.
        wildcardMatchers.add(Pattern.compile(regex));

        // Let'ReadOnlySettingParser match this also literally
        allMatchers.add(a);
      } else {
        // A plain word can be matched as string
        allMatchers.add(a.trim());
      }
    }
  }

  public static MatcherWithWildcards fromSettings(Settings s, String key) throws RuleNotConfiguredException {
    // Will work with single, non array conf.
    String[] a = s.getAsArray(key);

    if (a == null || a.length == 0) {
      throw new RuleNotConfiguredException();
    }
    return new MatcherWithWildcards(Sets.newHashSet(a));

  }

  /**
   * Returns null if the matchable is not worth processing because it'ReadOnlySettingParser invalid or starts with "-"
   */
  private static String normalizePlusAndMinusIndex(String s) {
    if (ConfigurationHelper.isNullOrEmpty(s)) {
      return null;
    }
    // Ignore the excluded indices
    if (s.startsWith("-")) {
      return null;
    }
    // Call included indices with their name
    if (s.startsWith("+")) {
      if (s.length() == 1) {
        logger.warn("invalid matchable! " + s);
        return null;
      }
      return s.substring(1, s.length());
    }
    return s;
  }

  public String matchWithResult(String matchable) {

    matchable = normalizePlusAndMinusIndex(matchable);

    if (matchable == null) {
      return null;
    }

    // Try to match plain strings first
    if (allMatchers.contains(matchable)) {
      return matchable;
    }

    for (Pattern p : wildcardMatchers) {
      Matcher m = p.matcher(matchable);
      if (m == null) {
        continue;
      }
      if (m.find()) {
        return matchable;
      }
    }

    return null;
  }

  public boolean match(String s) {
    return matchWithResult(s) != null;
  }


  public Set<String> filter(Set<String> haystack){
    if(haystack.isEmpty()) return empty;
    Set<String> res = Sets.newHashSet();
    for(String s: haystack){
      if(match(s)){
        res.add(s);
      }
    }
    return res;
  }

}

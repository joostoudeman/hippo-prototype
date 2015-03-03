package com.tdclighthouse.prototype.utils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * MIME-Type Parser
 * 
 * This class provides basic functions for handling mime-types. It can handle
 * matching mime-types against a list of media-ranges. See section 14.1 of the
 * HTTP specification [RFC 2616] for a complete explanation.
 * 
 * http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.1
 * 
 * A port to Java of Joe Gregorio's MIME-Type Parser:
 * 
 * http://code.google.com/p/mimeparse/
 * 
 * Ported by Tom Zellman <tzellman@gmail.com>.
 * 
 * 
 * This code has been adopted from and it is licensed under <a
 * href="http://opensource.org/licenses/mit-license.php">MIT License</a>
 * 
 */
public final class MIMEParse {

    private static final String ASTERISK = "*";

    /**
     * Parse results container
     */
    protected static class ParseResults {
        String type;

        String subType;

        // !a dictionary of all the parameters for the media range
        Map<String, String> params;

        @Override
        public String toString() {
            StringBuilder s = new StringBuilder("('" + type + "', '" + subType + "', {");
            for (String k : params.keySet()) {
                s.append("'" + k + "':'" + params.get(k) + "',");
            }
            return s.append("})").toString();
        }
    }

    /**
     * Carves up a mime-type and returns a ParseResults object
     * 
     * For example, the media range 'application/xhtml;q=0.5' would get parsed
     * into:
     * 
     * ('application', 'xhtml', {'q', '0.5'})
     */
    protected static ParseResults parseMimeType(String mimeType) {
        String[] parts = StringUtils.split(mimeType, ";");
        ParseResults results = new ParseResults();
        results.params = new HashMap<String, String>();

        for (int i = 1; i < parts.length; ++i) {
            String p = parts[i];
            String[] subParts = StringUtils.split(p, '=');
            if (subParts.length == 2) {
                results.params.put(subParts[0].trim(), subParts[1].trim());
            }
        }
        String fullType = parts[0].trim();

        // Java URLConnection class sends an Accept header that includes a
        // single "*" - Turn it into a legal wildcard.
        if (fullType.equals(ASTERISK)) {
            fullType = "*/*";
        }
        String[] types = StringUtils.split(fullType, "/");
        results.type = types[0].trim();
        results.subType = types[1].trim();
        return results;
    }

    /**
     * Carves up a media range and returns a ParseResults.
     * 
     * For example, the media range 'application/*;q=0.5' would get parsed into:
     * 
     * ('application', '*', {'q', '0.5'})
     * 
     * In addition this function also guarantees that there is a value for 'q'
     * in the params dictionary, filling it in with a proper default if
     * necessary.
     * 
     * @param range
     */
    protected static ParseResults parseMediaRange(String range) {
        ParseResults results = parseMimeType(range);
        String q = results.params.get("q");
        float f = NumberUtils.toFloat(q, 1);
        if (StringUtils.isBlank(q) || f < 0 || f > 1) {
            results.params.put("q", "1");
        }
        return results;
    }

    /**
     * Structure for holding a fitness/quality combo
     */
    protected static class FitnessAndQuality implements Comparable<FitnessAndQuality> {
        int fitness;

        float quality;

        // optionally used
        String mimeType;

        public FitnessAndQuality(int fitness, float quality) {
            this.fitness = fitness;
            this.quality = quality;
        }

        public int compareTo(FitnessAndQuality o) {
            if (fitness == o.fitness) {
                if (quality == o.quality) {
                    return 0;
                } else {
                    return quality < o.quality ? -1 : 1;
                }
            } else {
                return fitness < o.fitness ? -1 : 1;
            }
        }

        @Override
        public boolean equals(Object obj) {
            boolean result;
            if (obj instanceof FitnessAndQuality) {
                FitnessAndQuality rhs = (FitnessAndQuality) obj;
                result = new EqualsBuilder().appendSuper(super.equals(obj)).append(fitness, rhs.fitness)
                        .append(quality, rhs.quality).isEquals();
            } else {
                result = false;
            }
            return result;
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(93, 131).append(fitness).append(quality).toHashCode();
        }
    }

    /**
     * Find the best match for a given mimeType against a list of media_ranges
     * that have already been parsed by MimeParse.parseMediaRange(). Returns a
     * tuple of the fitness value and the value of the 'q' quality parameter of
     * the best match, or (-1, 0) if no match was found. Just as for
     * quality_parsed(), 'parsed_ranges' must be a list of parsed media ranges.
     * 
     * @param mimeType
     * @param parsedRanges
     */
    protected static FitnessAndQuality fitnessAndQualityParsed(String mimeType, Collection<ParseResults> parsedRanges) {
        int bestFitness = -1;
        float bestFitQ = 0;
        ParseResults target = parseMediaRange(mimeType);

        for (ParseResults range : parsedRanges) {
            if ((target.type.equals(range.type) || range.type.equals(ASTERISK) || target.type.equals(ASTERISK))
                    && (target.subType.equals(range.subType) || range.subType.equals(ASTERISK) || target.subType
                            .equals(ASTERISK))) {
                for (String k : target.params.keySet()) {
                    int paramMatches = 0;
                    if (!"q".equals(k) && range.params.containsKey(k)
                            && target.params.get(k).equals(range.params.get(k))) {
                        paramMatches++;
                    }
                    int fitness = (range.type.equals(target.type)) ? 100 : 0;
                    fitness += (range.subType.equals(target.subType)) ? 10 : 0;
                    fitness += paramMatches;
                    if (fitness > bestFitness) {
                        bestFitness = fitness;
                        bestFitQ = NumberUtils.toFloat(range.params.get("q"), 0);
                    }
                }
            }
        }
        return new FitnessAndQuality(bestFitness, bestFitQ);
    }

    /**
     * Find the best match for a given mime-type against a list of ranges that
     * have already been parsed by parseMediaRange(). Returns the 'q' quality
     * parameter of the best match, 0 if no match was found. This function
     * bahaves the same as quality() except that 'parsed_ranges' must be a list
     * of parsed media ranges.
     * 
     * @param mimeType
     * @param parsedRanges
     * @return
     */
    protected static float qualityParsed(String mimeType, Collection<ParseResults> parsedRanges) {
        return fitnessAndQualityParsed(mimeType, parsedRanges).quality;
    }

    /**
     * Returns the quality 'q' of a mime-type when compared against the
     * mediaRanges in ranges. For example:
     * 
     * @param mimeType
     * @param parsedRanges
     */
    public static float quality(String mimeType, String ranges) {
        List<ParseResults> results = new LinkedList<ParseResults>();
        for (String r : StringUtils.split(ranges, ',')) {
            results.add(parseMediaRange(r));
        }
        return qualityParsed(mimeType, results);
    }

    /**
     * Takes a list of supported mime-types and finds the best match for all the
     * media-ranges listed in header. The value of header must be a string that
     * conforms to the format of the HTTP Accept: header. The value of
     * 'supported' is a list of mime-types.
     * 
     * MimeParse.bestMatch(Arrays.asList(new String[]{"application/xbel+xml",
     * "text/xml"}), "text/*;q=0.5,*; q=0.1") 'text/xml'
     * 
     * @param supported
     * @param header
     * @return
     */
    public static String bestMatch(Collection<String> supported, String header) {
        List<ParseResults> parseResults = new LinkedList<ParseResults>();
        List<FitnessAndQuality> weightedMatches = new LinkedList<FitnessAndQuality>();
        for (String r : StringUtils.split(header, ',')) {
            parseResults.add(parseMediaRange(r));
        }
        for (String s : supported) {
            FitnessAndQuality fitnessAndQuality = fitnessAndQualityParsed(s, parseResults);
            fitnessAndQuality.mimeType = s;
            weightedMatches.add(fitnessAndQuality);
        }
        Collections.sort(weightedMatches);

        FitnessAndQuality lastOne = weightedMatches.get(weightedMatches.size() - 1);
        return NumberUtils.compare(lastOne.quality, 0) != 0 ? lastOne.mimeType : "";
    }

    // hidden
    private MIMEParse() {
    }
}
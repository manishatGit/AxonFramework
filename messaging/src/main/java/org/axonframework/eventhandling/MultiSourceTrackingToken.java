package org.axonframework.eventhandling;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.axonframework.common.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Combined tracking token used when processing from multiple event sources
 *
 * @author Greg Woods
 * @since 4.x
 */
public class MultiSourceTrackingToken implements Serializable, TrackingToken {

    private static final Logger logger = LoggerFactory.getLogger(MultiSourceTrackingToken.class);

    @JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS)
    private final Map<String, TrackingToken> trackingTokens;

    /**
     * Construct a new {@link MultiSourceTrackingToken} from a map of existing tokens.
     *
     * @param trackingTokens the map of tokens which make up the {@link MultiSourceTrackingToken}
     */
    @JsonCreator
    public MultiSourceTrackingToken(@JsonProperty("trackingTokens") Map<String, TrackingToken> trackingTokens) {
        this.trackingTokens = trackingTokens;
    }


    /**
     * Compares this token to {@code other} by comparing each member token with its counterpart in the {@code other}
     * token
     *
     * @param other The token to compare to this one
     * @return token representing the lower bound of of both tokens
     */
    @Override
    public TrackingToken lowerBound(TrackingToken other) {
        Assert.isTrue(other instanceof MultiSourceTrackingToken, () -> "Incompatible token type provided.");

        MultiSourceTrackingToken otherMultiToken = (MultiSourceTrackingToken) other;

        Assert.isTrue(otherMultiToken.trackingTokens.keySet().equals(this.trackingTokens.keySet()),
                      () -> "MultiSourceTrackingTokens contain different keys");

        Map<String, TrackingToken> tokenMap = new HashMap<>();

        otherMultiToken.trackingTokens.forEach((key, otherToken) ->
                                                       tokenMap.put(key, trackingTokens.get(key).lowerBound(otherToken))
        );

        return new MultiSourceTrackingToken(tokenMap);
    }

    /**
     * Compares this token to {@code other} by comparing each member token with its counterpart in the {@code other}
     * token
     *
     * @param other The token to compare this token to
     * @return a token that represents the furthest position of this or the other streams
     */
    @Override
    public TrackingToken upperBound(TrackingToken other) {
        Assert.isTrue(other instanceof MultiSourceTrackingToken, () -> "Incompatible token type provided.");

        MultiSourceTrackingToken otherMultiToken = (MultiSourceTrackingToken) other;

        Assert.isTrue(otherMultiToken.trackingTokens.keySet().equals(this.trackingTokens.keySet()),
                      () -> "MultiSourceTrackingTokens contain different keys");

        Map<String, TrackingToken> tokenMap = new HashMap<>();

        otherMultiToken.trackingTokens.forEach((key, otherToken) ->
                                                       tokenMap.put(key, trackingTokens.get(key).upperBound(otherToken))
        );

        return new MultiSourceTrackingToken(tokenMap);
    }

    /**
     * Compares this token to {@code other} checking each member token with its counterpart to see if they are covered
     * in the {@code other} token
     *
     * @param other The token to compare to this one
     * @return {@code true} if this token covers the other, otherwise {@code false}
     */
    @Override
    public boolean covers(TrackingToken other) {
        Assert.isTrue(other instanceof MultiSourceTrackingToken, () -> "Incompatible token type provided.");

        MultiSourceTrackingToken otherMultiToken = (MultiSourceTrackingToken) other;

        Assert.isTrue(otherMultiToken.trackingTokens.keySet().equals(this.trackingTokens.keySet()),
                      () -> "MultiSourceTrackingTokens contain different keys");

        //as soon as one delegated token doesn't cover return false
        for (Map.Entry<String, TrackingToken> trackingTokenEntry : trackingTokens.entrySet()) {
            if (!trackingTokenEntry.getValue().covers(otherMultiToken.trackingTokens
                                                              .get(trackingTokenEntry.getKey()))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Advances a single token within the tokenMap
     *
     * @param streamName        the stream/source which is being advanced
     * @param newTokenForStream the token representing the new position of the stream
     * @return the token representing the current processing position of all streams.
     */
    public MultiSourceTrackingToken advancedTo(String streamName, TrackingToken newTokenForStream) {
        trackingTokens.put(streamName, newTokenForStream);
        return new MultiSourceTrackingToken(trackingTokens);
    }

    /**
     * Return the tracking token for an individual stream
     *
     * @param streamName the name of the stream for the tracking token
     * @return the tracking token for the stream
     */
    public TrackingToken getTokenForStream(String streamName) {
        return trackingTokens.get(streamName);
    }

    /**
     * @return the map containing the constituent tokens.
     */
    public Map<String, TrackingToken> getTrackingTokens() {
        return trackingTokens;
    }

    /**
     * @return Sum of all positions of the tracking token
     */
    @Override
    public OptionalLong position() {

        //If all delegated tokens are empty then return empty
        if (trackingTokens.entrySet().stream().noneMatch(p -> p.getValue().position().isPresent())) {
            return OptionalLong.empty();
        }

        Long sumOfTokens = trackingTokens.entrySet().stream().map(c -> c.getValue().position().orElse(0L)).reduce(0L,
                                                                                                                  Long::sum);

        return OptionalLong.of(sumOfTokens);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MultiSourceTrackingToken that = (MultiSourceTrackingToken) o;

        if (this.trackingTokens.size() != that.trackingTokens.size()) {
            return false;
        }

        for (Map.Entry<String, TrackingToken> trackingTokenEntry : trackingTokens.entrySet()) {
            try {
                if (!trackingTokenEntry.getValue().equals(that.trackingTokens.get(trackingTokenEntry.getKey()))) {
                    return false;
                }
            } catch (NullPointerException ex) {
                logger.warn("A constituent token has not been initialized");
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(trackingTokens);
    }
}

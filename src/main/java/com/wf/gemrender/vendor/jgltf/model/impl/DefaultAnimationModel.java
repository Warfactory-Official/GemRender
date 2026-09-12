/*
 * www.javagl.de - JglTF
 *
 * Copyright 2015-2017 Marco Hutter - http://www.javagl.de
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */
package com.wf.gemrender.vendor.jgltf.model.impl;

import com.wf.gemrender.vendor.jgltf.model.AccessorModel;
import com.wf.gemrender.vendor.jgltf.model.AnimationModel;
import com.wf.gemrender.vendor.jgltf.model.NodeModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of an {@link AnimationModel}
 */
public class DefaultAnimationModel extends AbstractNamedModelElement
        implements AnimationModel {
    /**
     * The {@link com.wf.gemrender.vendor.jgltf.model.AnimationModel.Channel} instances
     * of this animation
     */
    private final List<Channel> channels;

    /**
     * Creates a new instance
     */
    public DefaultAnimationModel() {
        this.channels = new ArrayList<Channel>();
    }

    /**
     * Add the given {@link com.wf.gemrender.vendor.jgltf.model.AnimationModel.Channel}
     *
     * @param channel The {@link com.wf.gemrender.vendor.jgltf.model.AnimationModel.Channel}
     */
    public void addChannel(Channel channel) {
        Objects.requireNonNull(channel, "The channel may not be null");
        this.channels.add(channel);
    }

    @Override
    public List<Channel> getChannels() {
        return Collections.unmodifiableList(channels);
    }

    /**
     * Default implementation of a
     * {@link Sampler}
     *
     * @param input         The input data
     * @param interpolation The interpolation method
     * @param output        The output data
     */
        public record DefaultSampler(AccessorModel input, Interpolation interpolation,
                                     AccessorModel output) implements Sampler {
            /**
             * Default constructor
             *
             * @param input         The input
             * @param interpolation The interpolation
             * @param output        The output
             */
            public DefaultSampler(
                    AccessorModel input,
                    Interpolation interpolation,
                    AccessorModel output) {
                this.input = Objects.requireNonNull(
                        input, "The input may not be null");
                this.interpolation = Objects.requireNonNull(
                        interpolation, "The interpolation may not be null");
                this.output = Objects.requireNonNull(
                        output, "The output may not be null");
            }
        }

    /**
     * Default implementation of a
     * {@link Channel}
     *
     * @param sampler   The sampler
     * @param nodeModel The node model
     * @param path      The path
     */
        public record DefaultChannel(Sampler sampler, NodeModel nodeModel, String path) implements Channel {
            /**
             * Default constructor
             *
             * @param sampler   The sampler
             * @param nodeModel The node model
             * @param path      The path
             */
            public DefaultChannel(
                    Sampler sampler,
                    NodeModel nodeModel,
                    String path) {
                this.sampler = Objects.requireNonNull(
                        sampler, "The sampler may not be null");
                this.nodeModel = nodeModel;
                this.path = Objects.requireNonNull(
                        path, "The path may not be null");

            }

        }

}
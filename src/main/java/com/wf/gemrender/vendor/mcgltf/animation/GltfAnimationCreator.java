package com.wf.gemrender.vendor.mcgltf.animation;

import java.util.ArrayList;
import java.util.List;

import com.wf.gemrender.GemRender;

import com.wf.gemrender.vendor.jgltf.model.AccessorData;
import com.wf.gemrender.vendor.jgltf.model.AccessorFloatData;
import com.wf.gemrender.vendor.jgltf.model.AccessorModel;
import com.wf.gemrender.vendor.jgltf.model.AnimationModel;
import com.wf.gemrender.vendor.jgltf.model.NodeModel;
import com.wf.gemrender.vendor.jgltf.model.AnimationModel.Channel;
import com.wf.gemrender.vendor.jgltf.model.AnimationModel.Interpolation;
import com.wf.gemrender.vendor.jgltf.model.AnimationModel.Sampler;

/**
 * Turns an {@code AnimationModel} into samplers, paired with what each one targets.
 *
 * <p>GEMRENDER: upstream returned bare {@code InterpolatedChannel}s, each an anonymous subclass closing over
 * its {@code NodeModel} so that sampling wrote straight into the node -- twelve near-identical anonymous
 * classes, one per (path, interpolation) pair. Sampling is now a pure function that writes where it is
 * told, so a channel and its target are two separate facts and the creator returns both. That collapses
 * the twelve classes into four constructor calls and is what allows a caller to bind a channel to
 * per-evaluation storage instead of to the shared node graph.
 */
public final class GltfAnimationCreator {

	/** Channel target path: node translation, three components. */
	public static final String TRANSLATION_PATH = "translation";

	/** Channel target path: node rotation, four components, a quaternion. */
	public static final String ROTATION_PATH = "rotation";

	/** Channel target path: node scale, three components. */
	public static final String SCALE_PATH = "scale";

	/** Channel target path: morph target weights, one component per target. */
	public static final String WEIGHTS_PATH = "weights";

	/**
	 * One sampler and the node property it drives.
	 *
	 * @param components how many floats the channel produces per key, which is the accessor's own element
	 *                   width. A caller binding this to a shorter destination must pass the shorter count.
	 */
	public record ChannelBinding(InterpolatedChannel channel, NodeModel node, String path, int components) {
	}

	private GltfAnimationCreator() {
	}

	public static List<ChannelBinding> createGltfAnimation(AnimationModel animationModel) {
		List<Channel> channels = animationModel.getChannels();
		List<ChannelBinding> bindings = new ArrayList<>(channels.size());

		for(Channel channel : channels) {
			Sampler sampler = channel.getSampler();

			AccessorModel input = sampler.getInput();
			AccessorData inputData = input.getAccessorData();
			if (!(inputData instanceof AccessorFloatData))
			{
				GemRender.LOGGER.warn("Input data is not an AccessorFloatData, but "
						+ (inputData == null ? "null" : inputData.getClass()));
				continue;
			}
			AccessorFloatData inputFloatData = (AccessorFloatData)inputData;

			AccessorModel output = sampler.getOutput();
			AccessorData outputData = output.getAccessorData();
			if (!(outputData instanceof AccessorFloatData))
			{
				GemRender.LOGGER.warn("Output data is not an AccessorFloatData, but "
						+ (outputData == null ? "null" : outputData.getClass()));
				continue;
			}
			AccessorFloatData outputFloatData = (AccessorFloatData)outputData;

			int numKeyElements = inputFloatData.getNumElements();
			if (numKeyElements == 0)
			{
				GemRender.LOGGER.warn("Animation channel has no keyframes; skipping");
				continue;
			}
			float[] keys = new float[numKeyElements];
			for(int e = 0; e < numKeyElements; e++) {
				keys[e] = inputFloatData.get(e);
			}

			String path = channel.getPath();
			Interpolation interpolation = sampler.getInterpolation();

			int components;
            switch (path) {
                case TRANSLATION_PATH, ROTATION_PATH, SCALE_PATH ->
                        components = outputData.getNumComponentsPerElement();
                case WEIGHTS_PATH -> components = interpolation == Interpolation.CUBICSPLINE
                        ? outputData.getTotalNumComponents() / numKeyElements / 3
                        : outputData.getTotalNumComponents() / numKeyElements;
                default -> {
                    GemRender.LOGGER.warn("Animation channel target path must be "
                            + "\"translation\", \"rotation\", \"scale\" or  \"weights\", "
                            + "but is " + path);
                    continue;
                }
            }

			if (components <= 0) {
				GemRender.LOGGER.warn("Animation channel for '" + path + "' has no components; skipping");
				continue;
			}

			boolean rotation = ROTATION_PATH.equals(path);
			InterpolatedChannel interpolated;
            switch (interpolation) {
                case STEP -> interpolated = new StepInterpolatedChannel(keys,
                        readValues(outputFloatData, numKeyElements, components), rotation);
                case LINEAR ->
                        interpolated = rotation
                                ? new SphericalLinearInterpolatedChannel(keys,
                                readValues(outputFloatData, numKeyElements, components))
                                : new LinearInterpolatedChannel(keys,
                                readValues(outputFloatData, numKeyElements, components));
                case CUBICSPLINE -> interpolated = new CubicSplineInterpolatedChannel(keys,
                        readCubicValues(outputFloatData, numKeyElements, components), rotation);
                default -> {
                    GemRender.LOGGER.warn("Interpolation type not supported: " + interpolation);
                    continue;
                }
            }

			bindings.add(new ChannelBinding(interpolated, channel.getNodeModel(), path, components));
		}
		return bindings;
	}

	/** {@code [key][component]}. */
	private static float[][] readValues(AccessorFloatData data, int numKeyElements, int components) {
		float[][] values = new float[numKeyElements][components];
		int globalIndex = 0;
		for(int e = 0; e < numKeyElements; e++) {
			float[] element = values[e];
			for(int c = 0; c < components; c++) {
				element[c] = data.get(globalIndex++);
			}
		}
		return values;
	}

	/** {@code [key][in-tangent | value | out-tangent][component]}. */
	private static float[][][] readCubicValues(AccessorFloatData data, int numKeyElements, int components) {
		float[][][] values = new float[numKeyElements][3][components];
		int globalIndex = 0;
		for(int e = 0; e < numKeyElements; e++) {
			float[][] element = values[e];
			for(int i = 0; i < 3; i++) {
				float[] parts = element[i];
				for(int c = 0; c < components; c++) {
					parts[c] = data.get(globalIndex++);
				}
			}
		}
		return values;
	}
}

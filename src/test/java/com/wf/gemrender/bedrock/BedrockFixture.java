package com.wf.gemrender.bedrock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class BedrockFixture {

	static final String CHAIN = """
			{
			  "format_version": "1.12.0",
			  "minecraft:geometry": [
			    {
			      "description": {
			        "identifier": "geometry.chain",
			        "texture_width": 64,
			        "texture_height": 32
			      },
			      "bones": [
			        {
			          "name": "root",
			          "pivot": [1, 2, 3],
			          "rotation": [10, 20, 30],
			          "cubes": [
			            {"origin": [0, 0, 0], "size": [4, 5, 6], "uv": [0, 0]}
			          ]
			        },
			        {
			          "name": "arm",
			          "parent": "root",
			          "pivot": [7, 11, 13],
			          "rotation": [-5, 0, 45],
			          "cubes": [
			            {"origin": [6, 10, 12], "size": [2, 3, 4], "uv": [20, 0], "inflate": 0.5}
			          ]
			        },
			        {
			          "name": "hand",
			          "parent": "arm",
			          "pivot": [17, 19, 23],
			          "cubes": [
			            {"origin": [16, 18, 22], "size": [1, 1, 1], "uv": [40, 0],
			             "pivot": [17, 19, 23], "rotation": [0, 90, 0]}
			          ]
			        }
			      ]
			    }
			  ]
			}
			""";

	static final String LOOSE = """
			{
			  "format_version": "1.16.0",
			  "minecraft:geometry": [
			    {
			      "description": {"texture_width": 16, "texture_height": 16},
			      "bones": [
			        {"name": "child", "parent": "later", "pivot": [0, 8, 0],
			         "cubes": [{"origin": [0, 0, 0], "size": [1, 1, 1], "uv": [0, 0]}]},
			        {"name": "later", "pivot": [0, 0, 0]},
			        {"name": "orphan", "parent": "nobody", "pivot": [4, 4, 4],
			         "cubes": [{"origin": [0, 0, 0], "size": [1, 1, 1], "uv": [0, 0]}]}
			      ]
			    }
			  ]
			}
			""";

	static final String LEGACY = """
			{
			  "format_version": "1.8.0",
			  "geometry.creeper": {
			    "texturewidth": 64,
			    "textureheight": 32,
			    "bones": [
			      {"name": "body", "pivot": [0, 6, 0], "mirror": true,
			       "cubes": [{"origin": [-4, 0, -2], "size": [8, 12, 4], "uv": [16, 16]}]}
			    ]
			  }
			}
			""";

	static final String UNIT = """
			{
			  "format_version": "1.12.0",
			  "minecraft:geometry": [
			    {
			      "description": {"texture_width": 16, "texture_height": 16},
			      "bones": [
			        {"name": "cube", "pivot": [0, 0, 0],
			         "cubes": [{"origin": [-8, 0, -8], "size": [16, 16, 16], "uv": [0, 0]}]}
			      ]
			    }
			  ]
			}
			""";

	private BedrockFixture() {
	}

	static JsonObject json(String source) {
		return JsonParser.parseString(source)
				.getAsJsonObject();
	}

	static BedrockGeometry geometry(String source) {
		return BedrockGeometry.parse(json(source), "test");
	}

	static BedrockSkeleton skeleton(String source) {
		return BedrockSkeleton.of(geometry(source));
	}
}

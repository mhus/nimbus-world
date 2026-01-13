// Circle Marker Fragment Shader
precision highp float;

// Varyings
varying vec2 vUV;

// Uniforms
uniform vec3 color;
uniform float alpha;
uniform float fadeProgress; // 0-1: 0=invisible, 1=fully visible
uniform sampler2D textureSampler;
uniform bool useTexture;
uniform bool useCircleMask;

void main(void) {
    // Sample texture if provided
    vec4 texColor = vec4(1.0, 1.0, 1.0, 1.0);
    if (useTexture) {
        texColor = texture2D(textureSampler, vUV);
    }

    // Apply circular mask if requested
    if (useCircleMask) {
        vec2 centered = vUV - 0.5;
        float dist = length(centered) * 2.0; // 0 at center, 1 at edge
        float circle = 1.0 - smoothstep(0.9, 1.0, dist);
        texColor.a *= circle;
    }

    // Combine color with texture (multiply tint)
    vec3 finalColor = color * texColor.rgb;

    // Apply fade and alpha
    float finalAlpha = alpha * fadeProgress * texColor.a;

    // Alpha test - discard fully transparent pixels
    if (finalAlpha < 0.1) {
        discard;
    }

    gl_FragColor = vec4(finalColor, finalAlpha);
}

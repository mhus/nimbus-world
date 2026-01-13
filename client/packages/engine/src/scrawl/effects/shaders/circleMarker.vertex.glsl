// Circle Marker Vertex Shader
precision highp float;

// Attributes
attribute vec3 position;
attribute vec2 uv;

// Uniforms
uniform mat4 worldViewProjection;
uniform float radius;
uniform float time;
uniform float rotationSpeed;

// Varyings
varying vec2 vUV;

void main(void) {
    // Rotate UV around center
    float angle = time * rotationSpeed;
    float cosAngle = cos(angle);
    float sinAngle = sin(angle);

    vec2 centeredUV = uv - 0.5;
    vec2 rotatedUV = vec2(
        centeredUV.x * cosAngle - centeredUV.y * sinAngle,
        centeredUV.x * sinAngle + centeredUV.y * cosAngle
    );
    vUV = rotatedUV + 0.5;

    // Scale position by radius
    vec3 scaledPosition = position * radius;

    gl_Position = worldViewProjection * vec4(scaledPosition, 1.0);
}

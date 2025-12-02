#ifdef GL_ES
    precision mediump float;
#endif

// Uniforms
uniform float time;
uniform vec2 resolution;
uniform vec2 mouse;
uniform float pixelDensity;  // For high-DPI displays

// Constants
#define PI 3.14159265359
#define TAU (2.0 * PI)
#define PIXEL_SIZE 2.0  // Base pixel size, will be scaled by resolution

// Quantization function for retro pixelation
vec2 quantize(vec2 pos, float pixels) {
    vec2 pixelScale = resolution.xy / pixels;
    return floor(pos * pixelScale + 0.5) / pixelScale;
}

// Star field function by Patricio Gonzalez Vivo
// https://www.shadertoy.com/view/llXyWr

#define NUM_LAYERS 4.

float rand(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

float star(vec2 uv, float flare) {
    float d = length(uv);
    float m = 0.05 / d;
    
    // Star glow
    float rays = max(0., 1. - abs(uv.x * uv.y * 1000.));
    m += rays * flare * 0.3;
    
    // Center glow
    m *= smoothstep(1., 0.2, d);
    
    return m;
}

vec3 starLayer(vec2 uv) {
    vec3 col = vec3(0);
    
    // Create a grid
    vec2 gv = fract(uv) - 0.5;
    vec2 id = floor(uv);
    
    // Random position within the grid cell
    vec2 offset = vec2(rand(id), rand(id + 1.));
    
    // Animate
    float t = time * 0.1;
    offset = 0.5 + 0.5 * sin(t + 6.2831 * offset);
    
    // Star position
    vec2 starPos = (gv - offset + 0.5);
    
    // Star properties
    float star = star(starPos, 0.5);
    
    // Star color (slightly blue-tinted white)
    vec3 color = vec3(0.7, 0.8, 1.0) * star;
    
    // Add a subtle twinkle
    float twinkle = sin(time * 2.0 + id.x * 10.0 + id.y * 20.0) * 0.5 + 0.5;
    color *= 0.5 + 0.5 * twinkle;
    
    return color;
}

// Frame rate independent animation
float getTime() {
    // Normalize time to prevent floating point precision issues on long runs
    return mod(time, 3600.0);  // Reset every hour
}

void main() {
    // Apply pixelation
    vec2 pixelScale = resolution.xy / min(resolution.x, resolution.y) * PIXEL_SIZE;
    vec2 fragCoord = quantize(gl_FragCoord.xy, pixelScale.x);
    
    // Normalized pixel coordinates (0.0 to 1.0)
    vec2 uv = (fragCoord.xy - 0.5 * resolution.xy) / resolution.y;
    vec2 mousePos = (mouse.xy / resolution.xy) * 2.0 - 1.0;
    
    // Retro color palette (limited colors for authentic look)
    vec3[8] palette = vec3[](
        vec3(0.0, 0.0, 0.1),      // Deep space
        vec3(0.1, 0.0, 0.2),      // Dark purple
        vec3(0.2, 0.0, 0.4),      // Purple
        vec3(0.4, 0.0, 0.6),      // Bright purple
        vec3(0.2, 0.4, 1.0),      // Blue
        vec3(0.4, 0.6, 1.0),      // Light blue
        vec3(0.8, 0.8, 1.0),      // White-blue
        vec3(1.0, 1.0, 1.0)       // White
    );
    
    // Background gradient (darker blue to black)
    vec3 col = vec3(0.0);
    float d = length(uv);
    float gradient = smoothstep(0.5, 1.5, d);
    col = mix(palette[0], palette[1], gradient);
    
    // Frame rate independent time
    float t = getTime() * 0.5;  // Slowed down for better visibility
    
    // Add multiple layers of stars for parallax effect
    for(float i = 0.0; i < 1.0; i += 1.0/4.0) {
        // Frame rate independent animation
        float depth = fract(i + t * 0.02);
        float scale = mix(20.0, 0.5, i);
        float fade = depth * smoothstep(1.0, 0.9, depth);
        
        // Parallax effect with mouse movement
        vec2 offset = mousePos * 0.1 * i;
        
        // Apply pixelation to star positions for consistent retro look
        vec2 starPos = quantize(uv * scale + offset + i * 10.0, 100.0);
        vec3 stars = starLayer(starPos) * fade;
        
        // Retro color cycling (subtle)
        float hueShift = 0.5 + 0.5 * sin(t * 0.1 + i * TAU);
        stars *= mix(palette[2 + int(i * 4.0)], palette[3 + int(i * 4.0)], hueShift);
        
        // Dithering for retro feel
        float dither = (fract(sin(dot(fragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) / 64.0;
        stars += dither * fade;
        
        col = mix(col, stars, stars.r);
    }
    
    // Retro CRT screen effects
    // Scanlines
    float scanline = sin(uv.y * resolution.y * 1.0) * 0.05 + 0.95;
    col *= scanline;
    
    // Vignette with rounded corners (like old CRTs)
    vec2 uv2 = 2.0 * gl_FragCoord.xy / resolution.xy - 1.0;
    float vignette = 1.0 - smoothstep(0.7, 1.4, length(uv2));
    vignette *= smoothstep(-0.8, 0.8, uv2.x) * smoothstep(-0.8, 0.8, uv2.y);
    col *= mix(0.7, 1.0, vignette);
    
    // Color quantization to 8-bit (256 colors)
    col = floor(col * 16.0 + 0.5) / 16.0;
    
    // Output to screen
    gl_FragColor = vec4(col, 1.0);
}

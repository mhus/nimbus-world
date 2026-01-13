/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './src/**/*.{vue,js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {},
  },
  plugins: [require('daisyui')],
  daisyui: {
    themes: ['light', 'dark'], // Enable light and dark themes
    darkTheme: 'dark', // Default dark theme
    base: true,
    styled: true,
    utils: true,
  },
};

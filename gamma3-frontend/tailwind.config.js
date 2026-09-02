/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{html,ts}",
  ],
  theme: {
    extend: {
      colors: {
        'gamma-dark': '#0f172a',
        'gamma-primary': '#3b82f6',
        'gamma-accent': '#10b981'
      }
    },
  },
  plugins: [],
}

import globalData from '@csstools/postcss-global-data';
import customMedia from 'postcss-custom-media';

export default {
  plugins: [
    globalData({ files: ['src/shared/styles/breakpoints.css'] }),
    customMedia({ preserve: false }),
  ],
};

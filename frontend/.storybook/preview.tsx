import type { Preview } from '@storybook/react-vite';
import { LanguageScope } from '../src/shared/i18n/LanguageProvider';
import '../src/styles.css';
const preview: Preview = {
  globalTypes: {
    theme: {
      description: 'Theme',
      toolbar: { title: 'Theme', items: ['light', 'dark'], dynamicTitle: true },
    },
    language: {
      description: 'Language',
      toolbar: { title: 'Language', items: ['EN', 'SV'], dynamicTitle: true },
    },
  },
  initialGlobals: { theme: 'light', language: 'EN' },
  decorators: [
    (Story, context) => {
      document.documentElement.dataset.theme =
        context.globals.theme === 'dark' ? 'dark' : 'light';
      const language = context.globals.language === 'SV' ? 'SV' : 'EN';
      document.documentElement.lang = language === 'SV' ? 'sv' : 'en';
      return (
        <LanguageScope language={language}>
          <div className="story-surface">
            <Story />
          </div>
        </LanguageScope>
      );
    },
  ],
  parameters: { layout: 'padded', controls: { disable: true } },
};
export default preview;

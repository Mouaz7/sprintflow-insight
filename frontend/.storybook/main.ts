import type { StorybookConfig } from '@storybook/react-vite';
const config: StorybookConfig = {
  stories: ['../src/shared/ui/stories/**/*.stories.tsx'],
  framework: { name: '@storybook/react-vite', options: {} },
  core: { disableTelemetry: true },
  async viteFinal(config) {
    // The component catalogue shares the application Vite config but has its own browser origin.
    const plugins = (await Promise.all(config.plugins ?? [])).flat();
    return {
      ...config,
      plugins: plugins.filter(
        (plugin) =>
          !(
            plugin &&
            typeof plugin === 'object' &&
            'name' in plugin &&
            typeof plugin.name === 'string' &&
            plugin.name.startsWith('vite-plugin-pwa')
          ),
      ),
    };
  },
};
export default config;

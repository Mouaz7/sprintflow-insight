import { describe, expect, it } from 'vitest';
import { expectNoSeriousA11yViolations } from './axe';

describe('axe helper', () => {
  it('fails on a serious finding, so passing pages are really checked', async () => {
    const container = document.createElement('div');
    container.append(document.createElement('img'));
    const button = document.createElement('button');
    container.append(button);
    document.body.append(container);
    await expect(expectNoSeriousA11yViolations(container)).rejects.toThrow(
      /image-alt|button-name/,
    );
    container.remove();
  });
});

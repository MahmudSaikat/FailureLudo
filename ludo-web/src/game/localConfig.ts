import type { LocalGameConfig } from './useLocalGame';

// Holds the most recent local-game setup so LocalGameScreen survives a remount.
let current: LocalGameConfig | null = null;

export function setLocalConfig(config: LocalGameConfig) {
  current = config;
}

export function getLocalConfig(): LocalGameConfig | null {
  return current;
}

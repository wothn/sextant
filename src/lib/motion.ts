import {
  Easing,
  useReducedMotion as useReanimatedReducedMotion,
  type WithSpringConfig,
  type WithTimingConfig,
} from "react-native-reanimated";

export const MOTION_DURATION_FAST = 120;
export const MOTION_DURATION_BASE = 180;
export const MOTION_DURATION_SHEET_ENTER = 320;
export const MOTION_DURATION_SHEET_EXIT = 220;
export const MOTION_STAGGER_DELAY = 20;
export const MOTION_ENTER_OFFSET = 8;
export const MOTION_SHEET_OFFSET = 24;

const MOTION_EASING_ENTER = Easing.out(Easing.exp);
const MOTION_EASING_EXIT = Easing.in(Easing.cubic);
const MOTION_EASING_EMPHASIS = Easing.out(Easing.cubic);

export function useReducedMotion(): boolean {
  return useReanimatedReducedMotion();
}

export function getTimingConfig(
  duration: number,
  reduceMotion: boolean,
  easing = MOTION_EASING_EMPHASIS,
): WithTimingConfig {
  return {
    duration: reduceMotion ? 0 : duration,
    easing,
  };
}

export function getEnterTimingConfig(duration: number, reduceMotion: boolean): WithTimingConfig {
  return getTimingConfig(duration, reduceMotion, MOTION_EASING_ENTER);
}

export function getExitTimingConfig(duration: number, reduceMotion: boolean): WithTimingConfig {
  return getTimingConfig(duration, reduceMotion, MOTION_EASING_EXIT);
}

export function getSpringConfig(reduceMotion: boolean): WithSpringConfig {
  return reduceMotion
    ? {
        damping: 1000,
        stiffness: 1000,
        mass: 1,
      }
    : {
        damping: 18,
        stiffness: 240,
        mass: 0.9,
      };
}

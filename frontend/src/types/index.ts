export type StageZoneName =
  | 'CENTER'
  | 'LEFT_BACK'
  | 'RIGHT_BACK'
  | 'FRONT'
  | 'BACK'
  | 'LEFT_FRONT'
  | 'RIGHT_FRONT'
  | 'FULL_STAGE';

export type WindDirectionName =
  | 'N'
  | 'NE'
  | 'E'
  | 'SE'
  | 'S'
  | 'SW'
  | 'W'
  | 'NW'
  | 'STATIC'
  | 'CIRCULATE_CW'
  | 'CIRCULATE_CCW';

export interface StageZone {
  name: StageZoneName;
  displayName: string;
  centerX: number;
  centerY: number;
}

export interface WindDirection {
  name: WindDirectionName;
  displayName: string;
  vectorX: number;
  vectorY: number;
  circular: boolean;
}

export interface Fan {
  id: number;
  name: string;
  positionX: number;
  positionY: number;
  speedPercent: number;
  blowDirectionDegrees: number;
  active: boolean;
}

export interface SmokeMachine {
  id: number;
  name: string;
  assignedZone: StageZoneName;
  positionX: number;
  positionY: number;
  outputPercent: number;
  active: boolean;
  warmingUp: boolean;
}

export interface StageLayout {
  widthMeters: number;
  depthMeters: number;
  audienceSafeDistanceMeters: number;
  fans: Fan[];
  smokeMachines: SmokeMachine[];
}

export interface SmokeControlRequest {
  targetZone: StageZoneName;
  smokeFlowDirection: WindDirectionName;
  smokeDensityPercent: number;
  containmentStrengthPercent: number;
  audienceProtectionEnabled: boolean;
  durationSeconds: number;
}

export interface SmokeControlResponse {
  success: boolean;
  message: string;
  fanSettings: Fan[];
  smokeMachineSettings: SmokeMachine[];
  timestamp: string;
  windPatternDescription: string;
}

export interface SystemStatus {
  systemOnline: boolean;
  rs485Connected: boolean;
  stageStatus: {
    currentZone: string;
    currentFlowDirection: string;
    currentDensity: number;
    audienceProtected: boolean;
  };
  fanStatuses: Fan[];
  smokeMachineStatuses: SmokeMachine[];
}

export interface ManualFanAdjust {
  fanId: number;
  speedPercent?: number;
  blowDirectionDegrees?: number;
  active?: boolean;
}

export interface ManualSmokeAdjust {
  machineId: number;
  outputPercent?: number;
  active?: boolean;
}

export interface MusicStatus {
  enabled: boolean;
  currentDecibels: number;
  decibelFactor: number;
  maxWobblePercent: number;
  rhythmHz: number;
  tickIntervalMs: number;
  loopRunning: boolean;
}

export interface MusicConfig {
  enabled: boolean;
  initialDecibels?: number;
}

export interface MusicBeat {
  decibels: number;
}

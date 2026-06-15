import type {
  StageZone,
  WindDirection,
  StageLayout,
  SmokeControlRequest,
  SmokeControlResponse,
  SystemStatus,
  ManualFanAdjust,
  ManualSmokeAdjust,
  MusicStatus,
  MusicConfig,
  MusicBeat,
} from '../types';

const API_BASE = '/api';

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
    ...options,
  });
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}: ${res.statusText}`);
  }
  return (await res.json()) as T;
}

export const api = {
  getZones: () => request<StageZone[]>('/stage/zones'),
  getDirections: () => request<WindDirection[]>('/stage/directions'),
  getLayout: () => request<StageLayout>('/stage/layout'),
  getStatus: () => request<SystemStatus>('/stage/status'),
  applySmokeControl: (body: SmokeControlRequest) =>
    request<SmokeControlResponse>('/stage/smoke/apply', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  adjustFan: (body: ManualFanAdjust) =>
    request<SmokeControlResponse>('/stage/fan/adjust', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  adjustSmoke: (body: ManualSmokeAdjust) =>
    request<SmokeControlResponse>('/stage/smoke/adjust', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  emergencyStop: () =>
    request<SmokeControlResponse>('/stage/emergency-stop', {
      method: 'POST',
    }),
  health: () => request<{ status: string }>('/health'),
  getMusicStatus: () => request<MusicStatus>('/stage/music/status'),
  configureMusic: (body: MusicConfig) =>
    request<MusicStatus>('/stage/music/config', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  reportMusicBeat: (body: MusicBeat) =>
    request<MusicStatus>('/stage/music/beat', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
};

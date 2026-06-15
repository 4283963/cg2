import { useEffect, useState, useCallback } from 'react';
import { api } from './api/client';
import { StageCanvas } from './components/StageCanvas';
import { ControlPanel } from './components/ControlPanel';
import type {
  StageZone,
  WindDirection,
  Fan,
  SmokeMachine,
  StageZoneName,
  WindDirectionName,
  SmokeControlRequest,
} from './types';

const DEFAULT_LAYOUT = {
  widthMeters: 16,
  depthMeters: 10,
  audienceSafeDistanceMeters: 1,
  fans: [
    { id: 1, name: 'F1-左前角', positionX: -7.5, positionY: -4.5, speedPercent: 0, blowDirectionDegrees: 0, active: false },
    { id: 2, name: 'F2-左侧墙中', positionX: -7.5, positionY: 0, speedPercent: 0, blowDirectionDegrees: 0, active: false },
    { id: 3, name: 'F3-左后角', positionX: -7.5, positionY: 4.5, speedPercent: 0, blowDirectionDegrees: 0, active: false },
    { id: 4, name: 'F4-后墙左中', positionX: -3.5, positionY: 4.5, speedPercent: 0, blowDirectionDegrees: 0, active: false },
    { id: 5, name: 'F5-后墙右中', positionX: 3.5, positionY: 4.5, speedPercent: 0, blowDirectionDegrees: 0, active: false },
    { id: 6, name: 'F6-右后角', positionX: 7.5, positionY: 4.5, speedPercent: 0, blowDirectionDegrees: 0, active: false },
    { id: 7, name: 'F7-右侧墙中', positionX: 7.5, positionY: 0, speedPercent: 0, blowDirectionDegrees: 0, active: false },
    { id: 8, name: 'F8-右前角', positionX: 7.5, positionY: -4.5, speedPercent: 0, blowDirectionDegrees: 0, active: false },
  ] as Fan[],
  smokeMachines: [
    { id: 1, name: 'SM1-中央机', assignedZone: 'CENTER', positionX: 0, positionY: 0, outputPercent: 0, active: false, warmingUp: false },
    { id: 2, name: 'SM2-左背景机', assignedZone: 'LEFT_BACK', positionX: -5, positionY: 3, outputPercent: 0, active: false, warmingUp: false },
    { id: 3, name: 'SM3-右背景机', assignedZone: 'RIGHT_BACK', positionX: 5, positionY: 3, outputPercent: 0, active: false, warmingUp: false },
    { id: 4, name: 'SM4-前缘机', assignedZone: 'FRONT', positionX: 0, positionY: -3, outputPercent: 0, active: false, warmingUp: false },
  ] as SmokeMachine[],
};

function App() {
  const [zones, setZones] = useState<StageZone[]>([]);
  const [directions, setDirections] = useState<WindDirection[]>([]);
  const [fans, setFans] = useState<Fan[]>(DEFAULT_LAYOUT.fans);
  const [smokeMachines, setSmokeMachines] = useState<SmokeMachine[]>(DEFAULT_LAYOUT.smokeMachines);
  const [audienceSafeDistance, setAudienceSafeDistance] = useState(DEFAULT_LAYOUT.audienceSafeDistanceMeters);
  const [systemOnline, setSystemOnline] = useState(true);
  const [rs485Connected, setRs485Connected] = useState(false);

  const [selectedZone, setSelectedZone] = useState<StageZoneName | null>(null);
  const [selectedDirection, setSelectedDirection] = useState<WindDirectionName>('STATIC');
  const [smokeDensity, setSmokeDensity] = useState(50);
  const [containmentStrength, setContainmentStrength] = useState(80);
  const [durationSeconds, setDurationSeconds] = useState(0);
  const [audienceProtection, setAudienceProtection] = useState(true);

  const [applying, setApplying] = useState(false);
  const [statusMessage, setStatusMessage] = useState<{ type: 'success' | 'error' | ''; text: string }>({
    type: '',
    text: '',
  });

  const fetchMetadata = useCallback(async () => {
    try {
      const [zRes, dRes, lRes, sRes] = await Promise.all([
        api.getZones(),
        api.getDirections(),
        api.getLayout(),
        api.getStatus(),
      ]);
      setZones(zRes);
      setDirections(dRes);
      setFans(lRes.fans);
      setSmokeMachines(lRes.smokeMachines);
      setAudienceSafeDistance(lRes.audienceSafeDistanceMeters);
      setSystemOnline(sRes.systemOnline);
      setRs485Connected(sRes.rs485Connected);
      if (sRes.fanStatuses?.length) setFans(sRes.fanStatuses);
      if (sRes.smokeMachineStatuses?.length) setSmokeMachines(sRes.smokeMachineStatuses);
    } catch (e) {
      console.warn('初始化元数据失败，使用本地默认值', e);
      setZones([
        { name: 'LEFT_BACK', displayName: '左侧背景', centerX: -5, centerY: 3 },
        { name: 'BACK', displayName: '舞台后缘', centerX: 0, centerY: 4 },
        { name: 'RIGHT_BACK', displayName: '右侧背景', centerX: 5, centerY: 3 },
        { name: 'LEFT_FRONT', displayName: '左前区域', centerX: -4, centerY: -3 },
        { name: 'CENTER', displayName: '舞台中央', centerX: 0, centerY: 0 },
        { name: 'RIGHT_FRONT', displayName: '右前区域', centerX: 4, centerY: -3 },
        { name: 'FRONT', displayName: '舞台前缘', centerX: 0, centerY: -3 },
        { name: 'FULL_STAGE', displayName: '整个舞台', centerX: 0, centerY: 0 },
      ]);
      setDirections([
        { name: 'N', displayName: '向北', vectorX: 0, vectorY: 1, circular: false },
        { name: 'NE', displayName: '向东北', vectorX: 0.707, vectorY: 0.707, circular: false },
        { name: 'E', displayName: '向东', vectorX: 1, vectorY: 0, circular: false },
        { name: 'SE', displayName: '向东南', vectorX: 0.707, vectorY: -0.707, circular: false },
        { name: 'S', displayName: '向南', vectorX: 0, vectorY: -1, circular: false },
        { name: 'SW', displayName: '向西南', vectorX: -0.707, vectorY: -0.707, circular: false },
        { name: 'W', displayName: '向西', vectorX: -1, vectorY: 0, circular: false },
        { name: 'NW', displayName: '向西北', vectorX: -0.707, vectorY: 0.707, circular: false },
        { name: 'STATIC', displayName: '静止', vectorX: 0, vectorY: 0, circular: false },
        { name: 'CIRCULATE_CW', displayName: '顺时针旋转', vectorX: 0, vectorY: 0, circular: true },
        { name: 'CIRCULATE_CCW', displayName: '逆时针旋转', vectorX: 0, vectorY: 0, circular: true },
      ]);
    }
  }, []);

  useEffect(() => {
    fetchMetadata();
    const interval = setInterval(async () => {
      try {
        const status = await api.getStatus();
        setSystemOnline(status.systemOnline);
        setRs485Connected(status.rs485Connected);
        if (status.fanStatuses?.length) setFans([...status.fanStatuses]);
        if (status.smokeMachineStatuses?.length) setSmokeMachines([...status.smokeMachineStatuses]);
      } catch {
        setRs485Connected(false);
      }
    }, 2000);
    return () => clearInterval(interval);
  }, [fetchMetadata]);

  const handleZoneClick = (zone: StageZoneName) => {
    setSelectedZone(zone);
  };

  const handleApply = async () => {
    if (!selectedZone) return;
    setApplying(true);
    setStatusMessage({ type: '', text: '' });
    try {
      const body: SmokeControlRequest = {
        targetZone: selectedZone,
        smokeFlowDirection: selectedDirection,
        smokeDensityPercent: smokeDensity,
        containmentStrengthPercent: containmentStrength,
        audienceProtectionEnabled: audienceProtection,
        durationSeconds,
      };
      const resp = await api.applySmokeControl(body);
      if (resp.success) {
        if (resp.fanSettings) setFans([...resp.fanSettings]);
        if (resp.smokeMachineSettings) setSmokeMachines([...resp.smokeMachineSettings]);
        setStatusMessage({
          type: 'success',
          text: `✓ ${resp.windPatternDescription || '指令已下发成功'}`,
        });
      } else {
        setStatusMessage({ type: 'error', text: `✗ ${resp.message}` });
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setStatusMessage({ type: 'error', text: `✗ 网络错误: ${msg}` });
    } finally {
      setApplying(false);
    }
  };

  const handleEmergencyStop = async () => {
    try {
      const resp = await api.emergencyStop();
      if (resp.success) {
        if (resp.fanSettings) setFans([...resp.fanSettings]);
        if (resp.smokeMachineSettings) setSmokeMachines([...resp.smokeMachineSettings]);
        setStatusMessage({ type: 'success', text: '✓ 所有设备已紧急停止' });
      } else {
        setStatusMessage({ type: 'error', text: `✗ ${resp.message}` });
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setStatusMessage({ type: 'error', text: `✗ 网络错误: ${msg}` });
    }
  };

  return (
    <div className="app-root">
      <header className="app-header">
        <div className="app-title">
          <div style={{ fontSize: 28 }}>🎭</div>
          <div>
            <h1>先锋小剧场 · 舞台烟雾与风向控制系统</h1>
            <div className="app-subtitle">
              4台干冰造雾机 × 8台工业风机阵列 · RS485 Modbus RTU over TCP
            </div>
          </div>
        </div>
        <div className="header-actions">
          <div className="status-chip">
            <span className={`status-dot ${systemOnline ? '' : 'offline'}`} />
            <span>系统{systemOnline ? '运行中' : '离线'}</span>
          </div>
          <div className="status-chip">
            <span className={`status-dot ${rs485Connected ? '' : 'offline'}`} />
            <span>RS485网关{rs485Connected ? '已连接' : '未连接'}</span>
          </div>
        </div>
      </header>

      <div className="stage-container">
        <StageCanvas
          zones={zones}
          fans={fans}
          smokeMachines={smokeMachines}
          selectedZone={selectedZone}
          onZoneClick={handleZoneClick}
          audienceSafeDistanceMeters={audienceSafeDistance}
          windDirection={selectedDirection}
        />
      </div>

      <ControlPanel
        zones={zones}
        directions={directions}
        fans={fans}
        smokeMachines={smokeMachines}
        selectedZone={selectedZone}
        selectedDirection={selectedDirection}
        smokeDensity={smokeDensity}
        containmentStrength={containmentStrength}
        durationSeconds={durationSeconds}
        audienceProtection={audienceProtection}
        statusMessage={statusMessage}
        onZoneSelect={setSelectedZone}
        onDirectionSelect={setSelectedDirection}
        onSmokeDensityChange={setSmokeDensity}
        onContainmentChange={setContainmentStrength}
        onDurationChange={setDurationSeconds}
        onAudienceProtectionToggle={setAudienceProtection}
        onApply={handleApply}
        onEmergencyStop={handleEmergencyStop}
        applying={applying}
      />
    </div>
  );
}

export default App;

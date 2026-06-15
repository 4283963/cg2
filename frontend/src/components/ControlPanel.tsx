import React from 'react';
import type {
  StageZone,
  WindDirection,
  Fan,
  SmokeMachine,
  StageZoneName,
  WindDirectionName,
} from '../types';

interface ControlPanelProps {
  zones: StageZone[];
  directions: WindDirection[];
  fans: Fan[];
  smokeMachines: SmokeMachine[];
  selectedZone: StageZoneName | null;
  selectedDirection: WindDirectionName;
  smokeDensity: number;
  containmentStrength: number;
  durationSeconds: number;
  audienceProtection: boolean;
  statusMessage: { type: 'success' | 'error' | ''; text: string };
  onZoneSelect: (zone: StageZoneName) => void;
  onDirectionSelect: (dir: WindDirectionName) => void;
  onSmokeDensityChange: (v: number) => void;
  onContainmentChange: (v: number) => void;
  onDurationChange: (v: number) => void;
  onAudienceProtectionToggle: (v: boolean) => void;
  onApply: () => void;
  onEmergencyStop: () => void;
  applying: boolean;
}

const DIR_ARROWS: Record<WindDirectionName, string> = {
  N: '↑',
  NE: '↗',
  E: '→',
  SE: '↘',
  S: '↓',
  SW: '↙',
  W: '←',
  NW: '↖',
  STATIC: '✦',
  CIRCULATE_CW: '↻',
  CIRCULATE_CCW: '↺',
};

export const ControlPanel: React.FC<ControlPanelProps> = ({
  zones,
  directions,
  fans,
  smokeMachines,
  selectedZone,
  selectedDirection,
  smokeDensity,
  containmentStrength,
  durationSeconds,
  audienceProtection,
  statusMessage,
  onZoneSelect,
  onDirectionSelect,
  onSmokeDensityChange,
  onContainmentChange,
  onDurationChange,
  onAudienceProtectionToggle,
  onApply,
  onEmergencyStop,
  applying,
}) => {
  const eightDirs = directions.filter((d) => !d.circular && d.name !== 'STATIC');
  const specialDirs = directions.filter((d) => d.circular || d.name === 'STATIC');

  const buildDirectionPad = () => {
    const padLayout: (WindDirectionName | null)[][] = [
      ['NW', 'N', 'NE'],
      ['W', null, 'E'],
      ['SW', 'S', 'SE'],
    ];
    return padLayout.map((row, ri) => (
      <React.Fragment key={ri}>
        {row.map((cell, ci) =>
          cell ? (
            <button
              key={cell}
              className={`dir-btn ${selectedDirection === cell ? 'active' : ''}`}
              onClick={() => onDirectionSelect(cell)}
              title={directions.find((d) => d.name === cell)?.displayName}
            >
              {DIR_ARROWS[cell]}
            </button>
          ) : (
            <div key={`${ri}-${ci}`} className="dir-btn empty" />
          )
        )}
      </React.Fragment>
    ));
  };

  return (
    <div className="panel">
      <div className="panel-section">
        <div className="section-title">选择舞台区域</div>
        <div className="zone-grid">
          {zones.map((zone) => (
            <button
              key={zone.name}
              className={`zone-btn ${selectedZone === zone.name ? 'active' : ''}`}
              onClick={() => onZoneSelect(zone.name)}
            >
              {zone.displayName}
            </button>
          ))}
        </div>
      </div>

      <div className="panel-section">
        <div className="section-title">烟雾飘动方向</div>
        <div className="direction-pad">{buildDirectionPad()}</div>
        <div style={{ display: 'flex', gap: 8, marginTop: 12, justifyContent: 'center' }}>
          {specialDirs.map((dir) => (
            <button
              key={dir.name}
              className={`dir-btn ${selectedDirection === dir.name ? 'active' : ''}`}
              onClick={() => onDirectionSelect(dir.name)}
              title={dir.displayName}
              style={{ width: 'auto', padding: '0 14px', fontSize: 14 }}
            >
              <span style={{ marginRight: 6 }}>{DIR_ARROWS[dir.name]}</span>
              {dir.displayName}
            </button>
          ))}
        </div>
      </div>

      <div className="panel-section">
        <div className="section-title">参数微调</div>
        <div className="slider-group">
          <div className="slider-label">
            <span>烟雾浓度</span>
            <span className="slider-value">{smokeDensity}%</span>
          </div>
          <input
            type="range"
            min={0}
            max={100}
            value={smokeDensity}
            onChange={(e) => onSmokeDensityChange(Number(e.target.value))}
          />
        </div>
        <div className="slider-group">
          <div className="slider-label">
            <span>区域约束强度（围挡风力）</span>
            <span className="slider-value">{containmentStrength}%</span>
          </div>
          <input
            type="range"
            min={0}
            max={100}
            value={containmentStrength}
            onChange={(e) => onContainmentChange(Number(e.target.value))}
          />
        </div>
        <div className="slider-group">
          <div className="slider-label">
            <span>持续时长（0=手动关闭）</span>
            <span className="slider-value">{durationSeconds}s</span>
          </div>
          <input
            type="range"
            min={0}
            max={600}
            step={10}
            value={durationSeconds}
            onChange={(e) => onDurationChange(Number(e.target.value))}
          />
        </div>
        <div className="toggle-row">
          <span className="toggle-label">观众席保护（额外增强前缘围挡）</span>
          <div
            className={`toggle ${audienceProtection ? 'on' : ''}`}
            onClick={() => onAudienceProtectionToggle(!audienceProtection)}
          />
        </div>
      </div>

      <div className="panel-section">
        <div className="apply-row">
          <button
            className="btn btn-primary"
            onClick={onApply}
            disabled={applying || !selectedZone}
          >
            {applying ? '应用中...' : '应用特效'}
          </button>
          <button className="btn btn-danger" onClick={onEmergencyStop}>
            ⏹ 紧急停止
          </button>
        </div>
        {statusMessage.text && (
          <div className={`status-message ${statusMessage.type}`}>{statusMessage.text}</div>
        )}
      </div>

      <div className="panel-section">
        <div className="section-title">8台工业风机状态</div>
        <div className="device-list">
          {fans.map((fan) => (
            <div key={fan.id} className="device-item">
              <div className="device-info">
                <span className={`device-indicator ${fan.active ? 'active' : ''}`} />
                <span>{fan.name}</span>
              </div>
              <div className="device-stats">
                <div className="speed-bar">
                  <div
                    className="speed-bar-fill"
                    style={{ width: `${fan.speedPercent}%` }}
                  />
                </div>
                <span>{fan.speedPercent}%</span>
                <span>{Math.round(fan.blowDirectionDegrees)}°</span>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="panel-section">
        <div className="section-title">4台干冰造雾机状态</div>
        <div className="device-list">
          {smokeMachines.map((m) => (
            <div key={m.id} className="device-item">
              <div className="device-info">
                <span className={`device-indicator ${m.active ? 'active' : ''}`} />
                <span>{m.name}</span>
              </div>
              <div className="device-stats">
                <div className="speed-bar">
                  <div
                    className="speed-bar-fill smoke"
                    style={{ width: `${m.outputPercent}%` }}
                  />
                </div>
                <span>{m.outputPercent}%</span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

import React, { useEffect, useRef, useState, useCallback } from 'react';
import type {
  StageZone,
  Fan,
  SmokeMachine,
  StageZoneName,
  WindDirectionName,
} from '../types';

interface StageCanvasProps {
  zones: StageZone[];
  fans: Fan[];
  smokeMachines: SmokeMachine[];
  selectedZone: StageZoneName | null;
  onZoneClick: (zone: StageZoneName, clickX: number, clickY: number) => void;
  audienceSafeDistanceMeters: number;
  windDirection: WindDirectionName;
}

interface SmokeParticle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  life: number;
  maxLife: number;
  size: number;
  alpha: number;
  sourceZone: StageZoneName;
}

const ZONE_RADIUS_METERS = 3.2;
const FULL_STAGE_RADIUS = 7.0;

export const StageCanvas: React.FC<StageCanvasProps> = ({
  zones,
  fans,
  smokeMachines,
  selectedZone,
  onZoneClick,
  audienceSafeDistanceMeters,
  windDirection,
}) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const animFrameRef = useRef<number>(0);
  const particlesRef = useRef<SmokeParticle[]>([]);
  const [hoverCoord, setHoverCoord] = useState<{ x: number; y: number } | null>(null);

  const getStageBounds = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return { offsetX: 0, offsetY: 0, scale: 1, stageWidth: 16, stageHeight: 10 };
    const w = canvas.width / window.devicePixelRatio;
    const h = canvas.height / window.devicePixelRatio;
    const padding = 80;
    const availableW = w - padding * 2;
    const availableH = h - padding * 2;
    const stageW = 16;
    const stageH = 10;
    const scaleX = availableW / stageW;
    const scaleY = availableH / stageH;
    const scale = Math.min(scaleX, scaleY);
    const actualStageW = stageW * scale;
    const actualStageH = stageH * scale;
    const offsetX = (w - actualStageW) / 2;
    const offsetY = (h - actualStageH) / 2;
    return { offsetX, offsetY, scale, stageWidth: stageW, stageHeight: stageH };
  }, []);

  const stageToPixel = useCallback((x: number, y: number) => {
    const { offsetX, offsetY, scale, stageHeight } = getStageBounds();
    return {
      px: offsetX + (x + 8) * scale,
      py: offsetY + (stageHeight / 2 - y) * scale,
    };
  }, [getStageBounds]);

  const pixelToStage = useCallback((px: number, py: number) => {
    const { offsetX, offsetY, scale, stageHeight } = getStageBounds();
    return {
      x: (px - offsetX) / scale - 8,
      y: stageHeight / 2 - (py - offsetY) / scale,
    };
  }, [getStageBounds]);

  const getZoneAtPoint = useCallback((sx: number, sy: number): StageZoneName | null => {
    let hitZone: StageZoneName | null = null;
    let minDist = Infinity;
    for (const zone of zones) {
      const dx = sx - zone.centerX;
      const dy = sy - zone.centerY;
      const dist = Math.sqrt(dx * dx + dy * dy);
      const radius = zone.name === 'FULL_STAGE' ? FULL_STAGE_RADIUS : ZONE_RADIUS_METERS;
      if (dist <= radius && dist < minDist) {
        minDist = dist;
        hitZone = zone.name;
      }
    }
    const inStage = sx >= -8 && sx <= 8 && sy >= -5 && sy <= 5;
    return inStage ? hitZone : null;
  }, [zones]);

  const spawnSmokeParticles = useCallback(() => {
    const activeMachines = smokeMachines.filter((m) => m.active && m.outputPercent > 5);
    for (const machine of activeMachines) {
      const spawnCount = Math.max(1, Math.floor(machine.outputPercent / 20));
      for (let i = 0; i < spawnCount; i++) {
        const angle = Math.random() * Math.PI * 2;
        const r = Math.random() * 0.8;
        particlesRef.current.push({
          x: machine.positionX + Math.cos(angle) * r,
          y: machine.positionY + Math.sin(angle) * r,
          vx: (Math.random() - 0.5) * 0.03,
          vy: (Math.random() - 0.5) * 0.03,
          life: 0,
          maxLife: 180 + Math.random() * 120,
          size: 12 + Math.random() * 18,
          alpha: 0.08 + Math.random() * 0.12,
          sourceZone: machine.assignedZone,
        });
      }
    }
    if (particlesRef.current.length > 800) {
      particlesRef.current = particlesRef.current.slice(-800);
    }
  }, [smokeMachines]);

  const updateParticles = useCallback(() => {
    const surviving: SmokeParticle[] = [];
    const { stageHeight } = getStageBounds();
    const audienceLineY = -stageHeight / 2 + audienceSafeDistanceMeters;

    for (const p of particlesRef.current) {
      const distToCenter = Math.sqrt(p.x * p.x + p.y * p.y);
      const selected = zones.find((z) => z.name === selectedZone);
      let windX = 0;
      let windY = 0;

      if (selected) {
        const toCX = selected.centerX - p.x;
        const toCY = selected.centerY - p.y;
        const toCenterDist = Math.sqrt(toCX * toCX + toCY * toCY);
        const radius = selected.name === 'FULL_STAGE' ? FULL_STAGE_RADIUS : ZONE_RADIUS_METERS;
        if (toCenterDist > radius && toCenterDist > 0.001) {
          windX += (toCX / toCenterDist) * 0.015;
          windY += (toCY / toCenterDist) * 0.015;
        }
      }

      const dirMap: Record<WindDirectionName, [number, number]> = {
        N: [0, 0.008],
        NE: [0.006, 0.006],
        E: [0.008, 0],
        SE: [0.006, -0.006],
        S: [0, -0.008],
        SW: [-0.006, -0.006],
        W: [-0.008, 0],
        NW: [-0.006, 0.006],
        STATIC: [0, 0],
        CIRCULATE_CW: [0, 0],
        CIRCULATE_CCW: [0, 0],
      };
      const [fx, fy] = dirMap[windDirection] || [0, 0];
      windX += fx;
      windY += fy;

      if (windDirection === 'CIRCULATE_CW' || windDirection === 'CIRCULATE_CCW') {
        const sign = windDirection === 'CIRCULATE_CW' ? 1 : -1;
        const rx = p.x - (selected?.centerX ?? 0);
        const ry = p.y - (selected?.centerY ?? 0);
        windX += sign * ry * 0.002;
        windY += -sign * rx * 0.002;
      }

      if (p.y < audienceLineY) {
        windY += 0.025;
      }

      p.vx = p.vx * 0.97 + windX;
      p.vy = p.vy * 0.97 + windY;
      p.x += p.vx;
      p.y += p.vy;
      p.life += 1;

      if (p.x < -8) { p.x = -8; p.vx *= -0.5; }
      if (p.x > 8) { p.x = 8; p.vx *= -0.5; }
      if (p.y > 5) { p.y = 5; p.vy *= -0.5; }
      if (p.y < -5) { p.y = -5; p.vy *= -0.5; }

      if (p.life < p.maxLife) {
        surviving.push(p);
      }
    }
    particlesRef.current = surviving;
  }, [zones, selectedZone, windDirection, audienceSafeDistanceMeters, getStageBounds]);

  const draw = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const dpr = window.devicePixelRatio || 1;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const w = canvas.width / dpr;
    const h = canvas.height / dpr;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    const bgGrad = ctx.createRadialGradient(w / 2, h / 2, 0, w / 2, h / 2, Math.max(w, h) * 0.7);
    bgGrad.addColorStop(0, '#162038');
    bgGrad.addColorStop(1, '#0a0e1a');
    ctx.fillStyle = bgGrad;
    ctx.fillRect(0, 0, w, h);

    const { offsetX, offsetY, scale, stageHeight } = getStageBounds();

    const gridCountX = 16;
    const gridCountY = 10;
    ctx.strokeStyle = 'rgba(74, 158, 255, 0.06)';
    ctx.lineWidth = 1;
    for (let i = 0; i <= gridCountX; i++) {
      const x = offsetX + (i / gridCountX) * 16 * scale;
      ctx.beginPath();
      ctx.moveTo(x, offsetY);
      ctx.lineTo(x, offsetY + stageHeight * scale);
      ctx.stroke();
    }
    for (let i = 0; i <= gridCountY; i++) {
      const y = offsetY + (i / gridCountY) * stageHeight * scale;
      ctx.beginPath();
      ctx.moveTo(offsetX, y);
      ctx.lineTo(offsetX + 16 * scale, y);
      ctx.stroke();
    }

    ctx.save();
    ctx.shadowColor = 'rgba(74, 158, 255, 0.3)';
    ctx.shadowBlur = 20;
    ctx.strokeStyle = 'rgba(74, 158, 255, 0.6)';
    ctx.lineWidth = 2;
    ctx.strokeRect(offsetX, offsetY, 16 * scale, stageHeight * scale);
    ctx.restore();

    const audienceY = offsetY + (stageHeight / 2 - (-stageHeight / 2 + audienceSafeDistanceMeters)) * scale;
    ctx.fillStyle = 'rgba(251, 146, 60, 0.1)';
    ctx.fillRect(offsetX, audienceY, 16 * scale, stageHeight * scale - (audienceY - offsetY));
    ctx.strokeStyle = 'rgba(251, 146, 60, 0.6)';
    ctx.lineWidth = 2;
    ctx.setLineDash([8, 6]);
    ctx.beginPath();
    ctx.moveTo(offsetX, audienceY);
    ctx.lineTo(offsetX + 16 * scale, audienceY);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.fillStyle = 'rgba(251, 146, 60, 0.8)';
    ctx.font = `${12 * (Math.min(w, h) / 800)}px sans-serif`;
    ctx.textAlign = 'center';
    ctx.fillText('⚠ 观众席安全线 第一排观众勿越线', offsetX + 8 * scale, audienceY + 20);

    for (const zone of zones) {
      if (zone.name === 'FULL_STAGE') continue;
      const { px, py } = stageToPixel(zone.centerX, zone.centerY);
      const r = Math.max(1, ZONE_RADIUS_METERS * Math.abs(scale));
      const isSelected = zone.name === selectedZone;

      ctx.save();
      if (isSelected) {
        const pulse = 0.5 + 0.5 * Math.sin(Date.now() / 400);
        ctx.shadowColor = 'rgba(74, 158, 255, 0.8)';
        ctx.shadowBlur = 20 + pulse * 15;
        ctx.fillStyle = `rgba(74, 158, 255, ${0.15 + pulse * 0.1})`;
      } else {
        ctx.fillStyle = 'rgba(74, 158, 255, 0.04)';
      }
      ctx.beginPath();
      ctx.arc(px, py, r, 0, Math.PI * 2);
      ctx.fill();
      ctx.strokeStyle = isSelected ? 'rgba(74, 158, 255, 0.9)' : 'rgba(74, 158, 255, 0.15)';
      ctx.lineWidth = isSelected ? 2 : 1;
      ctx.setLineDash(isSelected ? [] : [4, 4]);
      ctx.stroke();
      ctx.setLineDash([]);
      ctx.restore();

      ctx.fillStyle = isSelected ? '#e8ecf4' : 'rgba(154, 167, 192, 0.6)';
      ctx.font = `${isSelected ? 600 : 400} ${11 * (Math.min(w, h) / 800)}px sans-serif`;
      ctx.textAlign = 'center';
      ctx.fillText(zone.displayName, px, py + 4);
    }

    if (selectedZone === 'FULL_STAGE') {
      const { px, py } = stageToPixel(0, 0);
      ctx.save();
      const pulse = 0.5 + 0.5 * Math.sin(Date.now() / 400);
      ctx.shadowColor = 'rgba(167, 139, 250, 0.6)';
      ctx.shadowBlur = 25 + pulse * 15;
      ctx.fillStyle = `rgba(167, 139, 250, ${0.08 + pulse * 0.04})`;
      ctx.fillRect(offsetX, offsetY, 16 * scale, stageHeight * scale);
      ctx.strokeStyle = 'rgba(167, 139, 250, 0.6)';
      ctx.lineWidth = 2;
      ctx.strokeRect(offsetX + 4, offsetY + 4, 16 * scale - 8, stageHeight * scale - 8);
      ctx.restore();
    }

    for (const p of particlesRef.current) {
      const { px, py } = stageToPixel(p.x, p.y);
      const lifeRatio = p.life / p.maxLife;
      const fade = lifeRatio < 0.2 ? lifeRatio / 0.2 : lifeRatio > 0.8 ? (1 - lifeRatio) / 0.2 : 1;
      const radius = Math.max(0.5, p.size * (1 + lifeRatio * 0.5) * (Math.abs(scale) / 60));
      const grad = ctx.createRadialGradient(px, py, 0, px, py, radius);
      grad.addColorStop(0, `rgba(220, 220, 240, ${p.alpha * fade})`);
      grad.addColorStop(0.5, `rgba(200, 200, 225, ${p.alpha * 0.6 * fade})`);
      grad.addColorStop(1, 'rgba(200, 200, 225, 0)');
      ctx.fillStyle = grad;
      ctx.beginPath();
      ctx.arc(px, py, radius, 0, Math.PI * 2);
      ctx.fill();
    }

    for (const fan of fans) {
      const { px, py } = stageToPixel(fan.positionX, fan.positionY);
      const safeScale = Math.max(1, Math.abs(scale));
      const fanRadius = Math.max(5, 14 * (safeScale / 60));

      ctx.save();
      if (fan.active) {
        ctx.shadowColor = fan.speedPercent > 60 ? 'rgba(34, 211, 238, 0.9)' : 'rgba(34, 211, 238, 0.5)';
        ctx.shadowBlur = 10 + (fan.speedPercent / 100) * 15;
      }
      ctx.fillStyle = fan.active ? 'rgba(34, 211, 238, 0.95)' : 'rgba(107, 120, 148, 0.5)';
      ctx.beginPath();
      ctx.arc(px, py, fanRadius, 0, Math.PI * 2);
      ctx.fill();
      ctx.strokeStyle = fan.active ? '#22d3ee' : 'rgba(107, 120, 148, 0.7)';
      ctx.lineWidth = 2;
      ctx.stroke();
      ctx.restore();

      if (fan.active && fan.speedPercent > 0) {
        const dirRad = (fan.blowDirectionDegrees * Math.PI) / 180;
        const arrowLen = Math.max(8, (fanRadius + 8) * (1 + fan.speedPercent / 100) * (safeScale / 60));
        const ax = px + Math.cos(dirRad) * arrowLen;
        const ay = py - Math.sin(dirRad) * arrowLen;

        ctx.save();
        ctx.strokeStyle = `rgba(34, 211, 238, ${0.5 + fan.speedPercent / 200})`;
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(px, py);
        ctx.lineTo(ax, ay);
        ctx.stroke();
        ctx.fillStyle = ctx.strokeStyle;
        const headLen = 6;
        const headAngle = Math.PI / 6;
        ctx.beginPath();
        ctx.moveTo(ax, ay);
        ctx.lineTo(
          ax - headLen * Math.cos(dirRad - headAngle),
          ay + headLen * Math.sin(dirRad - headAngle)
        );
        ctx.lineTo(
          ax - headLen * Math.cos(dirRad + headAngle),
          ay + headLen * Math.sin(dirRad + headAngle)
        );
        ctx.closePath();
        ctx.fill();
        ctx.restore();
      }

      ctx.fillStyle = '#0a0e1a';
      ctx.font = `bold ${Math.max(8, 10 * (safeScale / 60))}px sans-serif`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(`F${fan.id}`, px, py);

      ctx.fillStyle = fan.active ? '#22d3ee' : '#6b7894';
      ctx.font = `${Math.max(8, 10 * (safeScale / 60))}px sans-serif`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'top';
      ctx.fillText(`${fan.speedPercent}%`, px, py + fanRadius + 5);
    }

    for (const machine of smokeMachines) {
      const { px, py } = stageToPixel(machine.positionX, machine.positionY);
      const safeScale = Math.max(1, Math.abs(scale));
      const mW = Math.max(10, 22 * (safeScale / 60));
      const mH = Math.max(8, 18 * (safeScale / 60));

      ctx.save();
      if (machine.active) {
        ctx.shadowColor = 'rgba(167, 139, 250, 0.8)';
        ctx.shadowBlur = 12 + (machine.outputPercent / 100) * 12;
      }
      ctx.fillStyle = machine.active
        ? `rgba(167, 139, 250, ${0.7 + machine.outputPercent / 300})`
        : 'rgba(107, 120, 148, 0.5)';
      ctx.beginPath();
      const radius = Math.min(4, Math.min(mW, mH) / 3);
      ctx.roundRect(px - mW / 2, py - mH / 2, mW, mH, radius);
      ctx.fill();
      ctx.strokeStyle = machine.active ? '#a78bfa' : 'rgba(107, 120, 148, 0.7)';
      ctx.lineWidth = 2;
      ctx.stroke();
      ctx.restore();

      ctx.fillStyle = '#0a0e1a';
      ctx.font = `bold ${Math.max(7, 9 * (safeScale / 60))}px sans-serif`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(`SM${machine.id}`, px, py);

      ctx.fillStyle = machine.active ? '#a78bfa' : '#6b7894';
      ctx.font = `${Math.max(8, 10 * (safeScale / 60))}px sans-serif`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'bottom';
      ctx.fillText(`${machine.outputPercent}%`, px, py - mH / 2 - 4);
    }

    const safeScale = Math.max(1, Math.abs(scale));
    const labelFont = `${Math.max(9, 11 * (safeScale / 60))}px sans-serif`;
    ctx.font = labelFont;
    ctx.fillStyle = 'rgba(154, 167, 192, 0.8)';
    ctx.textAlign = 'center';
    ctx.fillText('← 舞台左侧墙', offsetX - 30, offsetY + stageHeight * scale / 2);
    ctx.fillText('舞台右侧墙 →', offsetX + 16 * scale + 30, offsetY + stageHeight * scale / 2);
    ctx.textAlign = 'center';
    ctx.fillText('↑ 后墙（舞台背景）', offsetX + 8 * scale, offsetY - 12);
  }, [zones, fans, smokeMachines, selectedZone, stageToPixel, getStageBounds, audienceSafeDistanceMeters]);

  const render = useCallback(() => {
    spawnSmokeParticles();
    updateParticles();
    draw();
    animFrameRef.current = requestAnimationFrame(render);
  }, [spawnSmokeParticles, updateParticles, draw]);

  useEffect(() => {
    const canvas = canvasRef.current;
    const wrapper = wrapperRef.current;
    if (!canvas || !wrapper) return;

    const resize = () => {
      const dpr = window.devicePixelRatio || 1;
      const rect = wrapper.getBoundingClientRect();
      canvas.width = rect.width * dpr;
      canvas.height = rect.height * dpr;
      canvas.style.width = `${rect.width}px`;
      canvas.style.height = `${rect.height}px`;
    };

    resize();
    window.addEventListener('resize', resize);
    animFrameRef.current = requestAnimationFrame(render);

    return () => {
      window.removeEventListener('resize', resize);
      cancelAnimationFrame(animFrameRef.current);
    };
  }, [render]);

  const handleClick = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const px = e.clientX - rect.left;
    const py = e.clientY - rect.top;
    const { x, y } = pixelToStage(px, py);
    const zoneName = getZoneAtPoint(x, y);
    if (zoneName) {
      onZoneClick(zoneName, x, y);
    }
  };

  const handleMouseMove = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const px = e.clientX - rect.left;
    const py = e.clientY - rect.top;
    const { x, y } = pixelToStage(px, py);
    if (x >= -8 && x <= 8 && y >= -5 && y <= 5) {
      setHoverCoord({ x: Math.round(x * 10) / 10, y: Math.round(y * 10) / 10 });
    } else {
      setHoverCoord(null);
    }
  };

  const handleMouseLeave = () => setHoverCoord(null);

  return (
    <div className="canvas-wrapper" ref={wrapperRef}>
      <canvas
        ref={canvasRef}
        className="stage-canvas"
        onClick={handleClick}
        onMouseMove={handleMouseMove}
        onMouseLeave={handleMouseLeave}
      />
      {hoverCoord && (
        <div className="coord-hint">
          坐标 X: {hoverCoord.x}m · Y: {hoverCoord.y}m
        </div>
      )}
      <div className="stage-legend">
        <div className="legend-item">
          <span className="legend-swatch zone" />
          <span>目标区域 (点击选择)</span>
        </div>
        <div className="legend-item">
          <span className="legend-swatch fan" />
          <span>工业风机 (带风向箭头)</span>
        </div>
        <div className="legend-item">
          <span className="legend-swatch smoke" />
          <span>干冰造雾机</span>
        </div>
        <div className="legend-item">
          <span className="legend-swatch audience" />
          <span>观众席安全区域</span>
        </div>
      </div>
    </div>
  );
};

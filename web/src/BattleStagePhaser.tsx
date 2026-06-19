import { useEffect, useRef } from 'react';
import Phaser from 'phaser';
import type { BattleFrame, DungeonRunResult } from './api';

type BattleStagePhaserProps = {
  result: DungeonRunResult;
  frame: BattleFrame;
};

type CombatantSide = 'player' | 'enemy';

const STAGE_WIDTH = 960;
const STAGE_HEIGHT = 540;

export function BattleStagePhaser({ result, frame }: BattleStagePhaserProps) {
  const hostRef = useRef<HTMLDivElement | null>(null);
  const gameRef = useRef<Phaser.Game | null>(null);
  const sceneRef = useRef<BattleScene | null>(null);

  useEffect(() => {
    if (!hostRef.current || gameRef.current) {
      return;
    }

    const scene = new BattleScene();
    sceneRef.current = scene;
    gameRef.current = new Phaser.Game({
      type: Phaser.AUTO,
      parent: hostRef.current,
      width: STAGE_WIDTH,
      height: STAGE_HEIGHT,
      backgroundColor: '#171923',
      scene,
      scale: {
        mode: Phaser.Scale.FIT,
        autoCenter: Phaser.Scale.CENTER_BOTH,
      },
      render: {
        antialias: true,
        pixelArt: false,
      },
    });

    return () => {
      gameRef.current?.destroy(true);
      gameRef.current = null;
      sceneRef.current = null;
    };
  }, []);

  useEffect(() => {
    sceneRef.current?.setFrame(result, frame);
  }, [result, frame]);

  return <div className="phaser-battle-stage" ref={hostRef} aria-label="2D 自动战斗画面" />;
}

class BattleScene extends Phaser.Scene {
  private ready = false;
  private player!: Phaser.GameObjects.Container;
  private enemy!: Phaser.GameObjects.Container;
  private playerName!: Phaser.GameObjects.Text;
  private enemyName!: Phaser.GameObjects.Text;
  private playerHp!: Phaser.GameObjects.Rectangle;
  private enemyHp!: Phaser.GameObjects.Rectangle;
  private playerHpText!: Phaser.GameObjects.Text;
  private enemyHpText!: Phaser.GameObjects.Text;
  private skillBanner!: Phaser.GameObjects.Text;
  private skillBannerBack!: Phaser.GameObjects.Rectangle;
  private roundText!: Phaser.GameObjects.Text;
  private playerBaseX = 250;
  private enemyBaseX = 710;
  private combatantY = 362;
  private lastFrameIndex = -1;
  private pending?: { result: DungeonRunResult; frame: BattleFrame };

  constructor() {
    super('battle-scene');
  }

  create() {
    this.drawArena();
    this.player = this.createHero(this.playerBaseX, this.combatantY);
    this.enemy = this.createMonster(this.enemyBaseX, this.combatantY);
    this.playerName = this.add.text(126, 48, '玩家', textStyle(18, '#e8f6ff')).setDepth(5);
    this.enemyName = this.add.text(638, 48, '敌人', textStyle(18, '#ffecec')).setDepth(5);
    this.playerHp = this.add.rectangle(126, 82, 260, 14, 0x51d88a).setOrigin(0, 0.5).setDepth(5);
    this.enemyHp = this.add.rectangle(638, 82, 260, 14, 0xff6262).setOrigin(0, 0.5).setDepth(5);
    this.playerHpText = this.add.text(126, 100, '', textStyle(13, '#b9c7d8')).setDepth(5);
    this.enemyHpText = this.add.text(638, 100, '', textStyle(13, '#b9c7d8')).setDepth(5);
    this.add.rectangle(126, 82, 260, 14, 0x0b101b, 0.65).setOrigin(0, 0.5).setStrokeStyle(1, 0xffffff, 0.18).setDepth(4);
    this.add.rectangle(638, 82, 260, 14, 0x0b101b, 0.65).setOrigin(0, 0.5).setStrokeStyle(1, 0xffffff, 0.18).setDepth(4);
    this.roundText = this.add.text(STAGE_WIDTH / 2, 40, '', textStyle(16, '#d8e4f5')).setOrigin(0.5).setDepth(6);
    this.skillBannerBack = this.add.rectangle(STAGE_WIDTH / 2, 128, 360, 46, 0x0a1020, 0.72)
      .setStrokeStyle(1, 0xffd166, 0.24)
      .setAlpha(0)
      .setDepth(9);
    this.skillBanner = this.add.text(STAGE_WIDTH / 2, 126, '', textStyle(25, '#ffffff'))
      .setOrigin(0.5)
      .setAlpha(0)
      .setDepth(10);
    this.ready = true;
    if (this.pending) {
      this.setFrame(this.pending.result, this.pending.frame);
      this.pending = undefined;
    }
  }

  setFrame(result: DungeonRunResult, frame: BattleFrame) {
    if (!this.ready) {
      this.pending = { result, frame };
      return;
    }
    this.playerName.setText(result.player.name);
    this.enemyName.setText(frame.enemyName || result.dungeonName);
    this.updateHp(frame);
    this.roundText.setText(frame.roomLabel ? `${frame.roomLabel} · 第 ${frame.index} 帧` : `第 ${frame.index} 帧`);
    if (frame.index === this.lastFrameIndex) {
      return;
    }
    this.lastFrameIndex = frame.index;
    this.playFrame(frame);
  }

  private drawArena() {
    const g = this.add.graphics();
    g.fillStyle(0x10131d, 1);
    g.fillRect(0, 0, STAGE_WIDTH, STAGE_HEIGHT);
    g.fillStyle(0x1c2231, 1);
    g.fillRect(0, 118, STAGE_WIDTH, 164);
    g.fillStyle(0x202b3c, 1);
    g.fillRect(0, 282, STAGE_WIDTH, 258);
    g.fillStyle(0x263950, 0.75);
    g.fillTriangle(0, 282, 170, 170, 340, 282);
    g.fillTriangle(230, 282, 480, 154, 730, 282);
    g.fillTriangle(620, 282, 820, 168, 960, 282);
    g.fillStyle(0x0c1018, 0.36);
    g.fillRect(0, 0, STAGE_WIDTH, 118);
    g.fillStyle(0x35445d, 0.55);
    for (let i = 0; i < 7; i += 1) {
      const x = 74 + i * 136;
      g.fillRect(x, 176, 18, 122);
      g.fillRect(x - 22, 172, 62, 8);
    }
    g.lineStyle(2, 0x7aa7ff, 0.12);
    for (let i = 0; i < 4; i += 1) {
      g.strokeEllipse(STAGE_WIDTH / 2, 404, 210 + i * 92, 78 + i * 34);
    }
    g.lineStyle(1, 0x9f8f67, 0.3);
    for (let i = 0; i < 9; i += 1) {
      g.lineBetween(70 + i * 104, 318, 20 + i * 116, STAGE_HEIGHT);
    }
    g.lineStyle(2, 0xe7c36a, 0.18);
    g.strokeEllipse(this.playerBaseX, 430, 270, 62);
    g.strokeEllipse(this.enemyBaseX, 430, 270, 62);
    g.fillStyle(0x000000, 0.22);
    g.fillEllipse(this.playerBaseX, 432, 300, 72);
    g.fillEllipse(this.enemyBaseX, 432, 300, 72);
    this.add.text(32, 486, 'AUTO BATTLE', textStyle(12, '#8798ae')).setAlpha(0.75);
  }

  private createHero(x: number, y: number) {
    const container = this.add.container(x, y).setDepth(4);
    container.setData('side', 'player');
    const shadow = this.add.ellipse(2, 72, 138, 30, 0x000000, 0.3);
    const aura = this.add.ellipse(0, 18, 126, 156, 0x61c5ff, 0.07);
    const cape = this.add.polygon(-30, -4, [-18, -80, -86, 16, -50, 70, -12, 52, 14, 10], 0x2458a8, 0.88)
      .setStrokeStyle(2, 0x89b8ff, 0.24);
    const capeFoldA = this.add.polygon(-46, 2, [-10, -54, -38, 10, -18, 58, 6, 20], 0x1c467f, 0.6);
    const capeFoldB = this.add.polygon(-18, 4, [-8, -64, -26, 2, -6, 50, 12, 12], 0x3772c8, 0.42);
    const backLeg = this.add.polygon(-17, 47, [-10, -22, 8, -22, 15, 31, 0, 42, -18, 33], 0x263a64, 1);
    const frontLeg = this.add.polygon(18, 48, [-10, -24, 10, -23, 17, 34, 2, 45, -16, 34], 0x315384, 1);
    const kneeA = this.add.ellipse(-15, 48, 22, 12, 0x83a3cf, 0.55).setRotation(0.2);
    const kneeB = this.add.ellipse(19, 50, 22, 12, 0x9cbce6, 0.55).setRotation(-0.1);
    const backBoot = this.add.polygon(-24, 78, [-16, -8, 14, -8, 22, 5, 3, 11, -24, 7], 0x121826, 1);
    const frontBoot = this.add.polygon(23, 80, [-15, -8, 16, -8, 27, 5, 5, 12, -23, 7], 0x121826, 1);
    const skirt = this.add.polygon(0, 28, [-34, -8, 34, -8, 24, 24, 6, 32, -8, 30, -28, 20], 0x24385d, 1)
      .setStrokeStyle(1, 0xbcd8ff, 0.16);
    const torso = this.add.polygon(0, -8, [-36, -56, 30, -64, 45, 8, 18, 44, -28, 39, -44, 4], 0x526f9f, 1)
      .setStrokeStyle(2, 0xdbe9ff, 0.24);
    const torsoShade = this.add.polygon(-11, -9, [-20, -48, 6, -55, 10, 36, -26, 32, -38, 2], 0x364f7b, 0.48);
    const chest = this.add.polygon(6, -18, [-25, -37, 22, -43, 31, -1, 5, 23, -19, 5], 0x96b7df, 0.96)
      .setStrokeStyle(1, 0xf5fbff, 0.28);
    const chestGem = this.add.polygon(9, -22, [0, -10, 10, 0, 0, 12, -10, 0], 0x77e0ff, 0.95)
      .setStrokeStyle(1, 0xffffff, 0.28);
    const chestTrim = this.add.rectangle(8, -4, 48, 5, 0xd5a84d, 0.9).setRotation(-0.1);
    const belt = this.add.rectangle(1, 25, 64, 10, 0x32261b, 1);
    const buckle = this.add.rectangle(2, 25, 15, 12, 0xd5a84d, 1).setStrokeStyle(1, 0xffe8a8, 0.35);
    const shoulder = this.add.ellipse(-34, -45, 34, 22, 0xc7d8ea, 0.96).setRotation(0.22)
      .setStrokeStyle(2, 0xffffff, 0.18);
    const shoulderTrim = this.add.ellipse(-35, -48, 22, 10, 0xeff7ff, 0.34).setRotation(0.2);
    const arm = this.add.polygon(38, -11, [-8, -30, 8, -31, 18, 20, 3, 36, -12, 14], 0x425f8e, 1);
    const forearmPlate = this.add.rectangle(48, 2, 18, 34, 0x9bb8d8, 0.92).setRotation(-0.48);
    const gauntlet = this.add.polygon(55, 14, [-10, -10, 12, -10, 16, 7, 2, 18, -14, 8], 0xc9d7e8, 1)
      .setRotation(-0.22);
    const offArm = this.add.polygon(-39, -4, [-8, -28, 8, -26, 14, 20, 1, 34, -13, 16], 0x314a78, 1);
    const shieldBack = this.add.polygon(-58, 10, [0, -33, 28, -10, 22, 31, 0, 49, -22, 31, -28, -10], 0x174b96, 0.92)
      .setStrokeStyle(3, 0x9ed5ff, 0.32);
    const shield = this.add.polygon(-56, 12, [0, -27, 22, -7, 17, 25, 0, 39, -17, 25, -22, -7], 0x2f80ed, 0.9);
    const shieldFacet = this.add.polygon(-57, 5, [0, -20, 20, -4, 3, 8, -16, 25, -21, -7], 0x4ba2ff, 0.55);
    const neck = this.add.rectangle(0, -61, 18, 16, 0xd7a982, 1);
    const hairBack = this.add.polygon(-7, -84, [-23, -6, -8, -25, 16, -20, 26, -4, 13, 14, -14, 13], 0x6b4526, 0.9);
    const head = this.add.ellipse(0, -82, 43, 48, 0xf0c89f, 1).setStrokeStyle(2, 0x111827, 0.38);
    const cheek = this.add.ellipse(11, -73, 13, 8, 0xf7d1aa, 0.78);
    const nose = this.add.triangle(17, -79, 0, -5, 10, 0, 0, 7, 0xd99d73, 0.95);
    const eye = this.add.rectangle(9, -85, 17, 4, 0x172033, 0.95);
    const brow = this.add.rectangle(9, -91, 19, 3, 0x5f3c24, 0.92).setRotation(-0.04);
    const mouth = this.add.rectangle(12, -70, 10, 2, 0x74392e, 0.75);
    const helmet = this.add.polygon(-1, -97, [-31, 4, -18, -18, 0, -25, 18, -19, 32, 3, 12, 14, -13, 13], 0xb7c7d9, 1)
      .setStrokeStyle(2, 0xffffff, 0.2);
    const helmetRim = this.add.rectangle(1, -94, 51, 7, 0xf2f7ff, 0.58).setRotation(-0.02);
    const plume = this.add.polygon(-22, -114, [-5, 24, 8, -14, 20, -28, 18, 16, 4, 30], 0x3aa0ff, 0.78)
      .setStrokeStyle(1, 0x9cd6ff, 0.24);
    const bladeGlow = this.add.polygon(94, -34, [-8, -12, 104, -18, 119, -6, 0, 10], 0x7ed8ff, 0.16);
    const blade = this.add.polygon(76, -34, [-4, -8, 90, -14, 107, -6, 0, 7], 0xeaf6ff, 1)
      .setStrokeStyle(2, 0x99d6ff, 0.38);
    const fuller = this.add.rectangle(118, -40, 46, 3, 0xaedcff, 0.72).setRotation(-0.08);
    const hilt = this.add.rectangle(51, -23, 38, 9, 0xd5a84d, 1).setRotation(-0.16);
    const grip = this.add.rectangle(42, -18, 28, 8, 0x243042, 1).setRotation(0.98);
    const swordGem = this.add.circle(55, -23, 4, 0x77e0ff, 1);
    container.add([
      shadow, aura, cape, capeFoldA, capeFoldB, backLeg, frontLeg, kneeA, kneeB, backBoot, frontBoot,
      skirt, torso, torsoShade, chest, chestGem, chestTrim, belt, buckle, shoulder, shoulderTrim,
      offArm, shieldBack, shield, shieldFacet, arm, forearmPlate, gauntlet, neck, hairBack, head,
      cheek, nose, eye, brow, mouth, plume, helmet, helmetRim, bladeGlow, blade, fuller, hilt, grip, swordGem,
    ]);
    this.addIdleMotion(container, 1.8);
    return container;
  }

  private createMonster(x: number, y: number) {
    const container = this.add.container(x, y).setDepth(4);
    container.setData('side', 'enemy');
    const shadow = this.add.ellipse(0, 72, 152, 30, 0x000000, 0.32);
    const aura = this.add.ellipse(0, 22, 142, 138, 0xff5f6d, 0.08);
    const abdomen = this.add.ellipse(39, 5, 86, 88, 0x823042, 1).setStrokeStyle(3, 0xff9aa5, 0.24);
    const abdomenGlow = this.add.ellipse(51, 0, 52, 52, 0xe04f68, 0.26);
    const abdomenMark = this.add.polygon(48, -4, [-14, -31, 18, -17, 21, 19, -9, 31, -25, 5], 0xbf4258, 0.82);
    const abdomenStripeA = this.add.rectangle(38, -28, 54, 5, 0x542334, 0.42).setRotation(0.2);
    const abdomenStripeB = this.add.rectangle(45, 10, 66, 5, 0x542334, 0.36).setRotation(-0.18);
    const body = this.add.ellipse(-18, 0, 98, 72, 0xa53b4f, 1).setStrokeStyle(3, 0xffb0b8, 0.25);
    const bodyHighlight = this.add.ellipse(-26, -16, 48, 24, 0xdc6374, 0.25).setRotation(-0.22);
    const thoraxPlateA = this.add.polygon(-20, -6, [-42, -8, -8, -30, 26, -12, 18, 14, -20, 20], 0x7a2c43, 0.6);
    const thoraxPlateB = this.add.polygon(-1, 12, [-38, -7, -6, -19, 36, -4, 24, 17, -24, 22], 0x542334, 0.38);
    const head = this.add.polygon(-68, -26, [-36, 8, -19, -30, 18, -36, 40, -5, 25, 27, -19, 30], 0x6e2637, 1)
      .setStrokeStyle(2, 0xffd0d0, 0.2);
    const crown = this.add.polygon(-63, -65, [-31, 18, -15, -17, 0, 15, 17, -20, 31, 17], 0xd5a84d, 0.82)
      .setStrokeStyle(1, 0xffe6a3, 0.28);
    const crownGem = this.add.polygon(-64, -48, [0, -7, 7, 0, 0, 8, -7, 0], 0xff665f, 0.9);
    const eyeA = this.add.ellipse(-79, -31, 9, 12, 0xffee88, 1);
    const eyeB = this.add.ellipse(-53, -34, 9, 12, 0xffee88, 1);
    const pupilA = this.add.ellipse(-79, -30, 3, 8, 0x1b1118, 0.95);
    const pupilB = this.add.ellipse(-53, -33, 3, 8, 0x1b1118, 0.95);
    const mandibleA = this.add.polygon(-88, -8, [-2, -8, -38, 2, -34, 14, 0, 6], 0x482033, 1)
      .setStrokeStyle(1, 0xff8899, 0.14);
    const mandibleB = this.add.polygon(-43, -8, [0, -7, 35, 0, 29, 13, -3, 6], 0x482033, 1)
      .setStrokeStyle(1, 0xff8899, 0.14);
    const fangA = this.add.triangle(-76, -5, 0, 0, 13, 0, 4, 29, 0xf7f0d9, 0.98);
    const fangB = this.add.triangle(-49, -7, 0, 0, 13, 0, 5, 29, 0xf7f0d9, 0.98);
    const venomA = this.add.circle(-72, 20, 4, 0x9dff79, 0.76);
    const venomB = this.add.circle(-45, 20, 3, 0x9dff79, 0.6);
    const legs: Phaser.GameObjects.GameObject[] = [];
    for (let i = 0; i < 4; i += 1) {
      const yOffset = -32 + i * 20;
      const reach = 72 + i * 8;
      const leftLeg = this.add.polygon(-18, yOffset, [0, 0, -reach, 18 + i * 4, -reach + 14, 32 + i * 4, -4, 10], 0x542334, 1)
        .setStrokeStyle(1, 0xff8899, 0.16);
      const rightLeg = this.add.polygon(10, yOffset, [0, 0, reach, 16 + i * 3, reach - 12, 31 + i * 3, 6, 10], 0x542334, 1)
        .setStrokeStyle(1, 0xff8899, 0.16);
      const leftJoint = this.add.circle(-18 - reach * 0.48, yOffset + 10 + i * 2, 7, 0x8a344a, 1)
        .setStrokeStyle(1, 0xffa0aa, 0.16);
      const rightJoint = this.add.circle(10 + reach * 0.48, yOffset + 9 + i * 2, 7, 0x8a344a, 1)
        .setStrokeStyle(1, 0xffa0aa, 0.16);
      legs.push(leftLeg, rightLeg, leftJoint, rightJoint);
    }
    container.add([
      shadow, aura, ...legs, abdomen, abdomenGlow, abdomenMark, abdomenStripeA, abdomenStripeB,
      body, bodyHighlight, thoraxPlateA, thoraxPlateB, mandibleA, mandibleB, head, crown,
      crownGem, eyeA, eyeB, pupilA, pupilB, fangA, fangB, venomA, venomB,
    ]);
    this.addIdleMotion(container, 2.2);
    return container;
  }

  private addIdleMotion(container: Phaser.GameObjects.Container, seconds: number) {
    this.tweens.add({
      targets: container,
      y: container.y - 6,
      duration: seconds * 1000,
      yoyo: true,
      repeat: -1,
      ease: 'Sine.easeInOut',
    });
  }

  private updateHp(frame: BattleFrame) {
    const playerRatio = ratio(frame.playerHp, frame.playerMaxHp);
    const enemyRatio = ratio(frame.enemyHp, frame.enemyMaxHp);
    this.playerHp.width = 260 * playerRatio;
    this.enemyHp.width = 260 * enemyRatio;
    this.playerHp.fillColor = playerRatio < 0.35 ? 0xffc857 : 0x51d88a;
    this.enemyHp.fillColor = enemyRatio < 0.35 ? 0xffa14a : 0xff6262;
    this.playerHpText.setText(`${frame.playerHp}/${frame.playerMaxHp}`);
    this.enemyHpText.setText(frame.enemyMaxHp > 0 ? `${frame.enemyHp}/${frame.enemyMaxHp}` : '');
  }

  private playFrame(frame: BattleFrame) {
    if (frame.eventType === 'phase' || frame.eventType === 'death') {
      this.flashBanner(frame.eventType === 'death' ? frame.text : frame.skillName || frame.text, frame.eventType === 'death' ? '#ffd166' : '#b8c7ff');
      if (frame.eventType === 'death') {
        const defeated = frame.targetSide === 'player' ? this.player : this.enemy;
        this.playDeathFx(defeated);
      }
      return;
    }
    if (frame.eventType === 'heal') {
      this.playHeal(frame);
      return;
    }
    if (frame.eventType === 'shield') {
      this.playShield(frame);
      return;
    }
    if (frame.actor === 'player' || frame.actor === 'enemy') {
      this.playAttack(frame);
    }
  }

  private playAttack(frame: BattleFrame) {
    const actor = frame.actor === 'player' ? this.player : this.enemy;
    const target = frame.actor === 'player' ? this.enemy : this.player;
    const direction = frame.actor === 'player' ? 1 : -1;
    const key = frame.visualKey || '';
    const isProjectile = hasAny(key, ['arrow', 'frost', 'spark', 'hex', 'meteor', 'bloodmoon', 'fire', 'bolt']);
    this.flashBanner(frame.skillName || battleEventName(frame), frame.critical ? '#ffe066' : '#eaf4ff');
    this.playCastFx(actor, frame.actor === 'enemy' ? 'enemy' : 'player');
    if (isProjectile) {
      this.tweens.add({
        targets: actor,
        x: actor.x - direction * 8,
        duration: 90,
        yoyo: true,
        ease: 'Sine.easeOut',
      });
    } else {
      this.tweens.add({
        targets: actor,
        x: actor.x + direction * 54,
        y: actor.y - 10,
        duration: 130,
        yoyo: true,
        ease: 'Cubic.easeOut',
      });
    }
    this.time.delayedCall(isProjectile ? 140 : 95, () => {
      if (frame.missed) {
        this.playDodge(target, -direction);
        this.floatText(target.x, target.y - 112, '闪避', '#d8e4f5');
        return;
      }
      this.drawAttackFx(frame, actor, target);
      this.playHitReaction(target, -direction, frame.critical);
      this.cameras.main.shake(frame.critical ? 170 : 90, frame.critical ? 0.007 : 0.003);
      this.floatText(target.x, target.y - 116, `${frame.critical ? '暴击 ' : ''}-${frame.damage}`, frame.critical ? '#ffe066' : '#ffb3a6');
    });
  }

  private playCastFx(actor: Phaser.GameObjects.Container, side: CombatantSide) {
    const color = side === 'player' ? 0x7ed8ff : 0xff798d;
    const ring = this.add.ellipse(actor.x, actor.y + 66, 104, 28).setStrokeStyle(3, color, 0.38).setDepth(3);
    const sparkA = this.add.triangle(actor.x - 42, actor.y + 26, 0, -8, 8, 8, -8, 8, color, 0.8).setDepth(7);
    const sparkB = this.add.triangle(actor.x + 36, actor.y - 12, 0, -7, 7, 7, -7, 7, color, 0.72).setDepth(7);
    this.tweens.add({
      targets: ring,
      scaleX: 1.25,
      scaleY: 1.45,
      alpha: 0,
      duration: 360,
      onComplete: () => ring.destroy(),
    });
    this.tweens.add({
      targets: [sparkA, sparkB],
      y: '-=24',
      alpha: 0,
      rotation: side === 'player' ? 1.4 : -1.4,
      duration: 420,
      ease: 'Cubic.easeOut',
      onComplete: () => {
        sparkA.destroy();
        sparkB.destroy();
      },
    });
  }

  private playDodge(target: Phaser.GameObjects.Container, direction: number) {
    const afterImage = this.add.ellipse(target.x, target.y - 4, 110, 140, 0xdfe9ff, 0.12).setDepth(3);
    this.tweens.add({
      targets: target,
      x: target.x + direction * 34,
      duration: 80,
      yoyo: true,
      ease: 'Sine.easeOut',
    });
    this.tweens.add({
      targets: afterImage,
      alpha: 0,
      scaleX: 1.2,
      duration: 280,
      onComplete: () => afterImage.destroy(),
    });
  }

  private playHitReaction(target: Phaser.GameObjects.Container, direction: number, critical: boolean) {
    const flash = this.add.ellipse(target.x, target.y - 18, 128, 150, critical ? 0xfff1a6 : 0xffffff, critical ? 0.22 : 0.14).setDepth(8);
    this.tweens.add({
      targets: target,
      x: target.x + direction * 18,
      duration: 52,
      yoyo: true,
      repeat: critical ? 2 : 1,
      ease: 'Sine.easeOut',
    });
    this.tweens.add({
      targets: flash,
      scale: 1.2,
      alpha: 0,
      duration: 220,
      onComplete: () => flash.destroy(),
    });
  }

  private drawAttackFx(frame: BattleFrame, actor: Phaser.GameObjects.Container, target: Phaser.GameObjects.Container) {
    const key = frame.visualKey || '';
    if (hasAny(key, ['meteor', 'bloodmoon'])) {
      this.playMeteorFx(frame, target);
      return;
    }
    if (hasAny(key, ['arrow', 'frost', 'spark', 'hex', 'fire', 'bolt'])) {
      this.playProjectileFx(frame, actor, target);
      return;
    }
    if (hasAny(key, ['armor', 'break'])) {
      this.playArmorBreakFx(target);
      return;
    }
    if (hasAny(key, ['execute'])) {
      this.playSlashFx(target, 0xffd166, true);
      this.time.delayedCall(90, () => this.playSlashFx(target, 0xff5f6d, true, true));
      return;
    }
    this.playSlashFx(target, frame.critical ? 0xfff1a6 : 0xdff3ff, frame.critical);
  }

  private playSlashFx(target: Phaser.GameObjects.Container, color: number, critical: boolean, reverse = false) {
    const fx = this.add.graphics({ x: target.x, y: target.y - 16 }).setDepth(9);
    fx.lineStyle(critical ? 9 : 6, color, 0.96);
    drawCurve(fx, reverse ? 72 : -72, -62, reverse ? -8 : 8, -104, reverse ? -74 : 74, 20);
    fx.lineStyle(3, 0xffffff, 0.68);
    drawCurve(fx, reverse ? 52 : -52, -42, reverse ? -4 : 4, -72, reverse ? -52 : 52, 12);
    const shock = this.add.ellipse(target.x, target.y + 50, 110, 26).setStrokeStyle(3, color, 0.35).setDepth(8);
    this.tweens.add({
      targets: fx,
      alpha: 0,
      scaleX: 1.18,
      scaleY: 1.18,
      duration: 680,
      ease: 'Cubic.easeOut',
      onComplete: () => fx.destroy(),
    });
    this.tweens.add({
      targets: shock,
      scaleX: 1.55,
      alpha: 0,
      duration: 620,
      onComplete: () => shock.destroy(),
    });
  }

  private playArmorBreakFx(target: Phaser.GameObjects.Container) {
    this.playSlashFx(target, 0xffd166, true);
    const cracks = this.add.graphics({ x: target.x, y: target.y - 30 }).setDepth(10);
    cracks.lineStyle(4, 0x1a1010, 0.9);
    cracks.lineBetween(-24, -40, 2, -12);
    cracks.lineBetween(2, -12, -18, 16);
    cracks.lineBetween(4, -10, 32, -32);
    cracks.lineBetween(4, -10, 28, 22);
    this.tweens.add({
      targets: cracks,
      alpha: 0,
      y: cracks.y + 10,
      duration: 680,
      delay: 80,
      onComplete: () => cracks.destroy(),
    });
    for (let i = 0; i < 8; i += 1) {
      const shard = this.add.triangle(target.x, target.y - 30, 0, -8, 8, 8, -8, 8, 0xd9e2ef, 0.95).setDepth(10);
      const angle = -Math.PI + (i / 7) * Math.PI * 2;
      this.tweens.add({
        targets: shard,
        x: target.x + Math.cos(angle) * Phaser.Math.Between(34, 82),
        y: target.y - 30 + Math.sin(angle) * Phaser.Math.Between(24, 62),
        rotation: angle * 1.8,
        alpha: 0,
        duration: 720,
        ease: 'Cubic.easeOut',
        onComplete: () => shard.destroy(),
      });
    }
  }

  private playProjectileFx(frame: BattleFrame, actor: Phaser.GameObjects.Container, target: Phaser.GameObjects.Container) {
    const key = frame.visualKey || '';
    const color = key.includes('frost') ? 0x9be7ff : key.includes('hex') ? 0xb58cff : key.includes('fire') ? 0xff9a3d : 0xf8d36d;
    const startX = actor.x + (frame.actor === 'player' ? 72 : -72);
    const startY = actor.y - 42;
    const endX = target.x;
    const endY = target.y - 34;
    const angle = Math.atan2(endY - startY, endX - startX);
    const trail = this.add.graphics().setDepth(7);
    trail.lineStyle(5, color, 0.26);
    trail.lineBetween(startX, startY, endX, endY);
    const bolt = this.add.container(startX, startY).setDepth(10);
    const head = this.add.circle(0, 0, 10, color, 1);
    const tail = this.add.rectangle(-22, 0, 42, 7, color, 0.72);
    bolt.add([tail, head]);
    bolt.setRotation(angle);
    this.tweens.add({
      targets: trail,
      alpha: 0,
      duration: 360,
      onComplete: () => trail.destroy(),
    });
    this.tweens.add({
      targets: bolt,
      x: endX,
      y: endY,
      duration: 260,
      ease: 'Sine.easeIn',
      onComplete: () => {
        bolt.destroy();
        this.playImpactBurst(endX, endY, color, key.includes('frost') ? 'snow' : 'spark');
      },
    });
  }

  private playMeteorFx(frame: BattleFrame, target: Phaser.GameObjects.Container) {
    const key = frame.visualKey || '';
    const color = key.includes('bloodmoon') ? 0xd83b5f : 0xff8f3d;
    const startX = target.x - 120;
    const startY = 96;
    const comet = this.add.container(startX, startY).setDepth(10);
    const tail = this.add.rectangle(-34, -8, 78, 14, color, 0.44).setRotation(-0.42);
    const core = this.add.circle(0, 0, 22, color, 0.95).setStrokeStyle(4, 0xfff0c4, 0.45);
    comet.add([tail, core]);
    this.tweens.add({
      targets: comet,
      x: target.x,
      y: target.y - 26,
      scale: 1.55,
      duration: 380,
      ease: 'Cubic.easeIn',
      onComplete: () => {
        comet.destroy();
        this.playImpactBurst(target.x, target.y - 22, color, 'ember');
        this.playGroundWave(target.x, target.y + 62, color);
      },
    });
  }

  private playImpactBurst(x: number, y: number, color: number, mode: 'spark' | 'snow' | 'ember') {
    const ring = this.add.circle(x, y, 18).setStrokeStyle(5, color, 0.72).setDepth(11);
    this.tweens.add({
      targets: ring,
      scale: mode === 'ember' ? 2.6 : 1.8,
      alpha: 0,
      duration: 620,
      ease: 'Cubic.easeOut',
      onComplete: () => ring.destroy(),
    });
    for (let i = 0; i < 12; i += 1) {
      const size = mode === 'snow' ? 5 : Phaser.Math.Between(4, 9);
      const particle = this.add.rectangle(x, y, size, size, mode === 'snow' ? 0xdff9ff : color, 0.95).setDepth(12);
      const angle = (i / 12) * Math.PI * 2;
      const distance = Phaser.Math.Between(34, mode === 'ember' ? 92 : 66);
      this.tweens.add({
        targets: particle,
        x: x + Math.cos(angle) * distance,
        y: y + Math.sin(angle) * distance,
        rotation: angle + 1.2,
        alpha: 0,
        duration: 760,
        ease: 'Cubic.easeOut',
        onComplete: () => particle.destroy(),
      });
    }
  }

  private playGroundWave(x: number, y: number, color: number) {
    const wave = this.add.ellipse(x, y, 80, 24).setStrokeStyle(5, color, 0.42).setDepth(8);
    this.tweens.add({
      targets: wave,
      scaleX: 2.35,
      scaleY: 1.55,
      alpha: 0,
      duration: 720,
      ease: 'Cubic.easeOut',
      onComplete: () => wave.destroy(),
    });
  }

  private playHeal(frame: BattleFrame) {
    const target = frame.targetSide === 'enemy' ? this.enemy : this.player;
    this.flashBanner(frame.skillName || '恢复', '#80ffbd');
    this.floatText(target.x, target.y - 118, `+${frame.effectValue || frame.damage}`, '#80ffbd');
    for (let i = 0; i < 3; i += 1) {
      const ring = this.add.ellipse(target.x, target.y + 26, 92 + i * 24, 30 + i * 8)
        .setStrokeStyle(4, 0x80ffbd, 0.64 - i * 0.12)
        .setDepth(7);
      this.tweens.add({
        targets: ring,
        scaleX: 1.4,
        scaleY: 1.7,
        alpha: 0,
        duration: 560,
        delay: i * 70,
        onComplete: () => ring.destroy(),
      });
    }
    for (let i = 0; i < 10; i += 1) {
      const leaf = this.add.polygon(target.x + Phaser.Math.Between(-52, 52), target.y + Phaser.Math.Between(-4, 62), [0, -8, 8, 0, 0, 10, -8, 0], 0x9dffca, 0.86).setDepth(9);
      this.tweens.add({
        targets: leaf,
        y: leaf.y - Phaser.Math.Between(56, 104),
        alpha: 0,
        rotation: Phaser.Math.FloatBetween(-1.4, 1.4),
        duration: 720,
        ease: 'Cubic.easeOut',
        onComplete: () => leaf.destroy(),
      });
    }
  }

  private playShield(frame: BattleFrame) {
    const target = frame.targetSide === 'enemy' ? this.enemy : this.player;
    this.flashBanner(frame.skillName || '护盾', '#9ecbff');
    this.floatText(target.x, target.y - 118, `护盾 ${frame.effectValue || 0}`, '#9ecbff');
    for (let i = 0; i < 2; i += 1) {
      const shield = this.add.polygon(target.x, target.y - 10, [0, -82, 70, -42, 70, 42, 0, 82, -70, 42, -70, -42], 0x2d74c4, 0.08)
        .setStrokeStyle(5 - i, 0x9ecbff, 0.72 - i * 0.22)
        .setDepth(8);
      this.tweens.add({
        targets: shield,
        scale: 1.16 + i * 0.14,
        rotation: i === 0 ? 0.18 : -0.18,
        alpha: 0,
        duration: 620,
        delay: i * 90,
        ease: 'Cubic.easeOut',
        onComplete: () => shield.destroy(),
      });
    }
  }

  private playDeathFx(target: Phaser.GameObjects.Container) {
    this.tweens.add({
      targets: target,
      alpha: 0.42,
      y: target.y + 18,
      rotation: target === this.enemy ? 0.08 : -0.08,
      duration: 360,
      ease: 'Cubic.easeOut',
    });
    this.playGroundWave(target.x, target.y + 68, 0xffd166);
  }

  private flashBanner(text: string, color: string) {
    const shortText = text.length > 30 ? `${text.slice(0, 28)}...` : text;
    this.skillBannerBack.setAlpha(0).setScale(0.94);
    this.skillBanner.setText(shortText).setColor(color).setAlpha(0).setScale(0.92);
    this.tweens.add({
      targets: [this.skillBanner, this.skillBannerBack],
      alpha: 1,
      scale: 1,
      duration: 120,
      yoyo: true,
      hold: 720,
      ease: 'Sine.easeOut',
    });
  }

  private floatText(x: number, y: number, text: string, color: string) {
    const fly = this.add.text(x, y, text, textStyle(25, color)).setOrigin(0.5).setDepth(12);
    this.tweens.add({
      targets: fly,
      y: y - 44,
      alpha: 0,
      duration: 780,
      ease: 'Cubic.easeOut',
      onComplete: () => fly.destroy(),
    });
  }
}

function textStyle(size: number, color: string): Phaser.Types.GameObjects.Text.TextStyle {
  return {
    fontFamily: 'Inter, "Microsoft YaHei", Arial, sans-serif',
    fontSize: `${size}px`,
    color,
    fontStyle: '700',
    stroke: '#10131c',
    strokeThickness: 4,
  };
}

function ratio(value: number, max: number) {
  if (max <= 0) {
    return 0;
  }
  return Phaser.Math.Clamp(value / max, 0, 1);
}

function battleEventName(frame: BattleFrame) {
  if (frame.missed) {
    return '闪避';
  }
  if (frame.critical) {
    return '暴击';
  }
  return frame.eventType === 'hit' ? '攻击' : frame.eventType;
}

function hasAny(value: string, needles: string[]) {
  return needles.some((needle) => value.includes(needle));
}

function drawCurve(graphics: Phaser.GameObjects.Graphics, startX: number, startY: number, controlX: number, controlY: number, endX: number, endY: number) {
  graphics.beginPath();
  graphics.moveTo(startX, startY);
  for (let i = 1; i <= 14; i += 1) {
    const t = i / 14;
    const x = (1 - t) * (1 - t) * startX + 2 * (1 - t) * t * controlX + t * t * endX;
    const y = (1 - t) * (1 - t) * startY + 2 * (1 - t) * t * controlY + t * t * endY;
    graphics.lineTo(x, y);
  }
  graphics.strokePath();
}

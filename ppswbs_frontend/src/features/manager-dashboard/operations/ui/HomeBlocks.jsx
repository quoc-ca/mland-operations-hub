import React, { useState } from 'react';
import { useHomeCards } from '../application/useHomeCards';
import RecentEvents from './RecentEvents';
import TodayStrip from './TodayStrip';
import './managerHome.css';

/**
 * The operations half of the Manager dashboard: today's operations, then the caller's revenue block, then recent events.
 * Each block loads and fails on its own. Read-only (spec 010 FR-015).
 */
export default function HomeBlocks({ revenue }) {
  const [branch, setBranch] = useState('all'); // kept for the visit only, not a saved preference (FR-008)
  const cards = useHomeCards({ branch });
  return (
    <>
      <TodayStrip strip={cards.section('strip')} locations={cards.section('locations')} branch={branch} onBranchChange={setBranch} onRetry={cards.reloadSection} />
      {revenue}
      <RecentEvents events={cards.section('events')} onRetry={cards.reloadSection} />
    </>
  );
}

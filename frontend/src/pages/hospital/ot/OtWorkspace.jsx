import React, { useCallback, useEffect, useState } from 'react';
import { useToast } from '../../../context/ToastContext';
import useOtPermissions from '../../../hooks/useOtPermissions';
import otService from '../../../services/otService';
import { extractApiError } from '../../../utils/apiError';
import { localIsoDate } from '../../../utils/date';
import AnaesthesiaClearanceModal from './AnaesthesiaClearanceModal';
import DayCareSurgeryModal from './DayCareSurgeryModal';
import OtAnalyticsStrip from './OtAnalyticsStrip';
import OtBoard from './OtBoard';
import OtDayBoard from './OtDayBoard';
import OtListPrint from './OtListPrint';
import RecoveryModal from './RecoveryModal';
import ScheduleSurgeryModal from './ScheduleSurgeryModal';
import SurgeryExecutionModal from './SurgeryExecutionModal';
import SurgeryTeamModal from './SurgeryTeamModal';

/**
 * The theatre-day workspace: scheduled/live board, day board, requests, and every action on a
 * case. Used by reception, and by a doctor who runs the theatre day themselves (Solo mode, or a
 * hospital with no receptionist / OT in-charge). Every action is offered only to a user who holds
 * the permission its endpoint requires, so the board never draws a button that would 403.
 */
export default function OtWorkspace() {
  const { error: toastError } = useToast();
  const { can: canOt } = useOtPermissions();
  const [otFilter, setOtFilter] = useState('board'); // 'board' | 'day' | 'requests'
  const [isDayCareModalOpen, setIsDayCareModalOpen] = useState(false);
  const [isOtListOpen, setIsOtListOpen] = useState(false);
  const [otTeamTarget, setOtTeamTarget] = useState(null);
  const [otExecTarget, setOtExecTarget] = useState(null);
  const [otRecoveryTarget, setOtRecoveryTarget] = useState(null);
  const [otRows, setOtRows] = useState([]);
  const [otLoading, setOtLoading] = useState(false);
  const [otScheduleTarget, setOtScheduleTarget] = useState(null);
  const [otAnaesthesiaTarget, setOtAnaesthesiaTarget] = useState(null);

  const loadOt = useCallback(() => {
    setOtLoading(true);
    const p = otFilter === 'requests' ? otService.getRequests() : otService.getBoard();
    p.then((d) => setOtRows(Array.isArray(d) ? d : []))
      .catch(() => setOtRows([]))
      .finally(() => setOtLoading(false));
  }, [otFilter]);

  useEffect(() => {
    loadOt();
  }, [loadOt]);

  return (
    <>
      <div>
        <div className="flex items-center justify-between mb-4">
          {/* Rendered by capability, not by role: a hospital grants OT_CREATE
                                to whoever books its day-care lists. */}
          <div className="flex items-center gap-2">
            {canOt('OT_CREATE') && (
              <button
                onClick={() => setIsDayCareModalOpen(true)}
                className="px-4 py-2 rounded-lg text-sm font-semibold bg-gray-900 text-white hover:bg-gray-800"
              >
                + Day-Care Surgery
              </button>
            )}
            <button
              onClick={() => setIsOtListOpen(true)}
              className="px-4 py-2 rounded-lg text-sm font-semibold border border-gray-300 text-gray-700 hover:bg-gray-50"
            >
              OT List
            </button>
          </div>
          <div className="inline-flex rounded-lg border border-gray-200 overflow-hidden">
            <button
              onClick={() => setOtFilter('board')}
              className={`px-4 py-1.5 text-sm font-semibold ${otFilter === 'board' ? 'bg-gray-900 text-white' : 'bg-white text-gray-600'}`}
            >
              Scheduled / Live
            </button>
            <button
              onClick={() => setOtFilter('day')}
              className={`px-4 py-1.5 text-sm font-semibold ${otFilter === 'day' ? 'bg-gray-900 text-white' : 'bg-white text-gray-600'}`}
            >
              Day Board
            </button>
            <button
              onClick={() => setOtFilter('requests')}
              className={`px-4 py-1.5 text-sm font-semibold ${otFilter === 'requests' ? 'bg-gray-900 text-white' : 'bg-white text-gray-600'}`}
            >
              Requests
            </button>
          </div>
        </div>
        <OtAnalyticsStrip refreshKey={otRows} />
        {otFilter === 'day' ? (
          <OtDayBoard date={localIsoDate()} onSelect={(c) => setOtExecTarget(c)} />
        ) : otLoading ? (
          <div className="text-center text-gray-400 py-16">Loading…</div>
        ) : (
          /* Each handler is supplied only when this user holds the permission its
           endpoint requires, so the board never draws a button that would 403. */
          <OtBoard
            rows={otRows}
            mode={otFilter}
            onSchedule={canOt('OT_SCHEDULE') ? (r) => setOtScheduleTarget(r) : undefined}
            onCancel={
              canOt('OT_CANCEL')
                ? async (r) => {
                    try {
                      await otService.cancel(r.publicId);
                      loadOt();
                    } catch (e) {
                      toastError(extractApiError(e, 'Failed to cancel'));
                    }
                  }
                : undefined
            }
            onStart={
              canOt('OT_START')
                ? async (r) => {
                    try {
                      await otService.start(r.publicId);
                      loadOt();
                    } catch (e) {
                      toastError(extractApiError(e, 'Failed to start'));
                    }
                  }
                : undefined
            }
            onComplete={
              canOt('OT_COMPLETE')
                ? async (r) => {
                    try {
                      await otService.complete(r.publicId);
                      loadOt();
                    } catch (e) {
                      toastError(extractApiError(e, 'Failed to complete'));
                    }
                  }
                : undefined
            }
            onTeam={canOt('OT_ASSIGN_TEAM') ? (r) => setOtTeamTarget(r) : undefined}
            onExecute={canOt('OT_VIEW') ? (r) => setOtExecTarget(r) : undefined}
            onRecovery={
              canOt('OT_RECOVERY') || canOt('OT_TRANSFER')
                ? (r) => setOtRecoveryTarget(r)
                : undefined
            }
            onClose={
              canOt('OT_CLOSE')
                ? async (r) => {
                    try {
                      await otService.close(r.publicId);
                      loadOt();
                    } catch (e) {
                      toastError(extractApiError(e, 'Failed to close'));
                    }
                  }
                : undefined
            }
            onApprove={
              canOt('OT_APPROVE')
                ? async (r) => {
                    try {
                      await otService.approve(r.publicId);
                      loadOt();
                    } catch (e) {
                      toastError(extractApiError(e, 'Failed to approve'));
                    }
                  }
                : undefined
            }
            onPreOp={
              canOt('OT_PRE_OP')
                ? async (r) => {
                    try {
                      await otService.preOp(r.publicId);
                      loadOt();
                    } catch (e) {
                      toastError(extractApiError(e, 'Failed to enter pre-op'));
                    }
                  }
                : undefined
            }
            onAnaesthesia={
              canOt('OT_ANAESTHESIA_CLEARANCE') ? (r) => setOtAnaesthesiaTarget(r) : undefined
            }
            onPostpone={
              canOt('OT_RESCHEDULE')
                ? async (r) => {
                    try {
                      await otService.postpone(r.publicId);
                      loadOt();
                    } catch (e) {
                      toastError(extractApiError(e, 'Failed to postpone'));
                    }
                  }
                : undefined
            }
          />
        )}
        {otAnaesthesiaTarget && (
          <AnaesthesiaClearanceModal
            surgery={otAnaesthesiaTarget}
            onClose={() => setOtAnaesthesiaTarget(null)}
            onRecorded={loadOt}
          />
        )}
        {otTeamTarget && (
          <SurgeryTeamModal surgery={otTeamTarget} onClose={() => setOtTeamTarget(null)} />
        )}
        {otExecTarget && (
          <SurgeryExecutionModal
            surgery={otExecTarget}
            onClose={() => {
              setOtExecTarget(null);
              loadOt();
            }}
          />
        )}
        {otRecoveryTarget && (
          <RecoveryModal
            surgery={otRecoveryTarget}
            onClose={() => {
              setOtRecoveryTarget(null);
              loadOt();
            }}
          />
        )}
        {otScheduleTarget && (
          <ScheduleSurgeryModal
            surgery={otScheduleTarget}
            onClose={() => setOtScheduleTarget(null)}
            onScheduled={loadOt}
          />
        )}
      </div>
      <DayCareSurgeryModal
        isOpen={isDayCareModalOpen}
        onClose={() => setIsDayCareModalOpen(false)}
        onSuccess={loadOt}
      />
      {isOtListOpen && <OtListPrint onClose={() => setIsOtListOpen(false)} />}
    </>
  );
}

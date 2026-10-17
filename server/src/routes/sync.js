import { Router } from 'express';
import { syncAll, pullSync } from '../controllers/syncController.js';

const router = Router();

/**
 * Offline-First Synchronization Routes
 */

// POST /api/sync - Unified two-way sync (push changes + resolve conflicts + pull updates)
router.post('/', syncAll);

// POST /api/sync/push - Push offline changes only
router.post('/push', syncAll);

// GET /api/sync/pull - Pull server updates since timestamp (?since=ISO_TIMESTAMP)
router.get('/pull', pullSync);

export default router;

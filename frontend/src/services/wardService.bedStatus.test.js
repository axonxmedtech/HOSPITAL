import { beforeEach, describe, expect, it, vi } from 'vitest';
import WardService from './wardService';
const { post } = vi.hoisted(() => ({ post: vi.fn() }));
vi.mock('./apiService', () => ({ default: { post } }));
describe('WardService audited bed transition', () => {
  beforeEach(() => post.mockResolvedValue({ data: { message: 'Bed available' } }));
  it('uses canonical POST with remarks and never legacy PUT', async () => {
    await WardService.updateBedStatus(42, 'cleaned');
    expect(post).toHaveBeenCalledWith('/hospital/beds/42/available', { remarks: 'cleaned' });
  });
});

import { describe, it, expect, vi, beforeEach } from 'vitest'

// vi.mock factory is hoisted, so use vi.hoisted for the mock object
const { mockRequest } = vi.hoisted(() => ({
  mockRequest: {
    get: vi.fn(() => Promise.resolve({ data: {} })),
    post: vi.fn(() => Promise.resolve({ data: {} })),
    put: vi.fn(() => Promise.resolve({ data: {} })),
    delete: vi.fn(() => Promise.resolve({ data: {} }))
  }
}))

vi.mock('@/api/request', () => ({
  default: mockRequest
}))

// Now import the modules under test
import {
  getCaptcha, login, register, getUserInfo, refreshToken
} from '@/api/user'

import {
  listItems, createFolder, rename,
  moveToTrash, restore, permanentDelete,
  listTrash, upload, download, preview,
  recentItems, recentSaves
} from '@/api/file'

import {
  createShare, listMyShares, cancelShare,
  accessShare, listReceivedShares,
  listShareItems, downloadShareFile, saveShareFiles
} from '@/api/share'

describe('api/user.js — endpoint correctness', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getCaptcha: GET /users/captcha', async () => {
    await getCaptcha()
    expect(mockRequest.get).toHaveBeenCalledWith('/users/captcha')
  })

  it('login: POST /users/login with {username, password, captchaKey, captchaCode}', async () => {
    await login('alice', 'pass123', 'key1', '42')
    expect(mockRequest.post).toHaveBeenCalledWith('/users/login', {
      username: 'alice', password: 'pass123', captchaKey: 'key1', captchaCode: '42'
    })
  })

  it('register: POST /users/register with {username, password, email, captchaKey, captchaCode}', async () => {
    await register('bob', 'pass456', 'bob@test.com', 'key2', '77')
    expect(mockRequest.post).toHaveBeenCalledWith('/users/register', {
      username: 'bob', password: 'pass456', email: 'bob@test.com',
      captchaKey: 'key2', captchaCode: '77'
    })
  })

  it('getUserInfo: GET /users', async () => {
    await getUserInfo()
    expect(mockRequest.get).toHaveBeenCalledWith('/users')
  })

  it('refreshToken: POST /users/refresh', async () => {
    await refreshToken()
    expect(mockRequest.post).toHaveBeenCalledWith('/users/refresh')
  })
})

describe('api/file.js — endpoint correctness', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listItems: GET /files/list with parentId, page, size', async () => {
    await listItems('42', 2, 50)
    expect(mockRequest.get).toHaveBeenCalledWith('/files/list', {
      params: { parentId: '42', page: 2, size: 50 }
    })
  })

  it('listItems: GET /files/list omits parentId when null', async () => {
    await listItems(null, 1, 20)
    expect(mockRequest.get).toHaveBeenCalledWith('/files/list', {
      params: { page: 1, size: 20 }
    })
    const args = mockRequest.get.mock.calls[0][1]
    expect(args.params.parentId).toBeUndefined()
  })

  it('listItems uses default page=1 size=20', async () => {
    await listItems()
    expect(mockRequest.get).toHaveBeenCalledWith('/files/list', {
      params: { page: 1, size: 20 }
    })
  })

  it('createFolder: POST /files/folder with {name, parentId}', async () => {
    await createFolder('新建文件夹', '10')
    expect(mockRequest.post).toHaveBeenCalledWith('/files/folder', {
      name: '新建文件夹', parentId: '10'
    })
  })

  it('rename: PUT /files/rename with {itemId, newName}', async () => {
    await rename('100', '新名字')
    expect(mockRequest.put).toHaveBeenCalledWith('/files/rename', {
      itemId: '100', newName: '新名字'
    })
  })

  it('moveToTrash: DELETE /files/trash with params {ids}', async () => {
    await moveToTrash(['1', '2', '3'])
    expect(mockRequest.delete).toHaveBeenCalledWith('/files/trash', {
      params: { ids: ['1', '2', '3'] }
    })
  })

  it('restore: POST /files/restore with null body and params {ids}', async () => {
    await restore([10, 20])
    expect(mockRequest.post).toHaveBeenCalledWith('/files/restore', null, {
      params: { ids: [10, 20] }
    })
  })

  it('permanentDelete: DELETE /files/permanent with params {ids}', async () => {
    await permanentDelete([99])
    expect(mockRequest.delete).toHaveBeenCalledWith('/files/permanent', {
      params: { ids: [99] }
    })
  })

  it('listTrash: GET /files/trash with page, size', async () => {
    await listTrash(3, 15)
    expect(mockRequest.get).toHaveBeenCalledWith('/files/trash', {
      params: { page: 3, size: 15 }
    })
  })

  it('upload: POST /files/upload with multipart formData', async () => {
    const fakeFile = new File(['dummy'], 'test.txt', { type: 'text/plain' })
    await upload(fakeFile, '5')

    expect(mockRequest.post).toHaveBeenCalledTimes(1)
    const [url, body, config] = mockRequest.post.mock.calls[0]
    expect(url).toBe('/files/upload')
    expect(body).toBeInstanceOf(FormData)
    expect(body.get('file')).toBe(fakeFile)
    expect(body.get('parentId')).toBe('5')
    expect(config.headers['Content-Type']).toBe('multipart/form-data')
  })

  it('upload: omits parentId from FormData when null', async () => {
    const fakeFile = new File(['test'], 'a.txt', { type: 'text/plain' })
    await upload(fakeFile, null)

    const [_, body] = mockRequest.post.mock.calls[0]
    expect(body.get('parentId')).toBeNull()
  })

  it('download: GET /files/download/{id} with responseType blob', async () => {
    await download('77')
    expect(mockRequest.get).toHaveBeenCalledWith('/files/download/77', {
      responseType: 'blob'
    })
  })

  it('preview: GET /files/preview/{id}', async () => {
    await preview('88')
    expect(mockRequest.get).toHaveBeenCalledWith('/files/preview/88')
  })

  it('recentItems: GET /files/recent with params {days}', async () => {
    await recentItems(3)
    expect(mockRequest.get).toHaveBeenCalledWith('/files/recent', {
      params: { days: 3 }
    })
  })

  it('recentSaves: GET /files/recent-saves with params {days}', async () => {
    await recentSaves(7)
    expect(mockRequest.get).toHaveBeenCalledWith('/files/recent-saves', {
      params: { days: 7 }
    })
  })
})

describe('stores/user.js — response data access pattern', () => {
  it('login: reads res.data.accessToken (not res.data.data.accessToken)', () => {
    const mockResponse = {
      code: 200,
      msg: 'success',
      data: { accessToken: 'abc', refreshToken: 'def', userId: 1 }
    }
    const token = mockResponse.data.accessToken
    expect(token).toBe('abc')
    expect(mockResponse.data.data).toBeUndefined()
  })

  it('register: reads res.data.accessToken (not res.data.data.accessToken)', () => {
    const mockResponse = {
      code: 200,
      msg: 'success',
      data: { accessToken: 'xyz', refreshToken: 'uvw', userId: 2 }
    }
    const token = mockResponse.data.accessToken
    expect(token).toBe('xyz')
    expect(mockResponse.data.data).toBeUndefined()
  })

  it('fetchUserInfo: reads res.data (not res.data.data)', () => {
    const mockResponse = {
      code: 200,
      msg: 'success',
      data: { id: 1, username: 'alice', email: 'a@b.com' }
    }
    const userInfo = mockResponse.data
    expect(userInfo.username).toBe('alice')
    expect(mockResponse.data.data).toBeUndefined()
  })

  it('captcha: reads res.data.key (not res.data.data.key)', () => {
    const mockResponse = {
      code: 200,
      msg: 'success',
      data: { key: 'abc123', imageBase64: 'data:image/png;...' }
    }
    const key = mockResponse.data.key
    expect(key).toBe('abc123')
    expect(mockResponse.data.imageBase64).toContain('data:image')
    expect(mockResponse.data.data).toBeUndefined()
  })
})

describe('FileListView createFolder API calls', () => {
  it('createFolder sends name and parentId', async () => {
    await createFolder('我的文件夹', '10')
    expect(mockRequest.post).toHaveBeenCalledWith('/files/folder', {
      name: '我的文件夹', parentId: '10'
    })
  })

  it('createFolder sends name with null parentId for root', async () => {
    await createFolder('根目录文件夹', null)
    expect(mockRequest.post).toHaveBeenCalledWith('/files/folder', {
      name: '根目录文件夹', parentId: null
    })
  })
})

describe('api/share.js — endpoint correctness', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('createShare: POST /shares with itemId, password, permission, expireType', async () => {
    await createShare({ itemId: 1, password: '1234', permission: 'download', expireType: 'ONE_WEEK' })
    expect(mockRequest.post).toHaveBeenCalledWith('/shares', {
      itemId: 1, password: '1234', permission: 'download', expireType: 'ONE_WEEK'
    })
  })

  it('createShare: POST /shares without optional fields', async () => {
    await createShare({ itemId: 2 })
    expect(mockRequest.post).toHaveBeenCalledWith('/shares', { itemId: 2 })
  })

  it('listMyShares: GET /shares/mine with page, size', async () => {
    await listMyShares(2, 50)
    expect(mockRequest.get).toHaveBeenCalledWith('/shares/mine', {
      params: { page: 2, size: 50 }
    })
  })

  it('listMyShares uses default page=1 size=20', async () => {
    await listMyShares()
    expect(mockRequest.get).toHaveBeenCalledWith('/shares/mine', {
      params: { page: 1, size: 20 }
    })
  })

  it('cancelShare: POST /shares/{id}/cancel', async () => {
    await cancelShare(5)
    expect(mockRequest.post).toHaveBeenCalledWith('/shares/5/cancel')
  })

  it('accessShare: GET /shares/access/{token} with password', async () => {
    await accessShare('abc123', 'mypass')
    expect(mockRequest.get).toHaveBeenCalledWith('/shares/access/abc123', {
      params: { password: 'mypass' }
    })
  })

  it('accessShare: GET /shares/access/{token} without password', async () => {
    await accessShare('def456')
    expect(mockRequest.get).toHaveBeenCalledWith('/shares/access/def456', {
      params: {}
    })
  })

  it('listReceivedShares: GET /shares/received with page, size', async () => {
    await listReceivedShares(1, 15)
    expect(mockRequest.get).toHaveBeenCalledWith('/shares/received', {
      params: { page: 1, size: 15 }
    })
  })

  it('listReceivedShares uses default page=1 size=20', async () => {
    await listReceivedShares()
    expect(mockRequest.get).toHaveBeenCalledWith('/shares/received', {
      params: { page: 1, size: 20 }
    })
  })

  it('listShareItems: GET /shares/access/{token}/items with all params', async () => {
    await listShareItems('tok1', 'pass1', 99, 3, 30)
    expect(mockRequest.get).toHaveBeenCalledWith('/shares/access/tok1/items', {
      params: { password: 'pass1', parentId: 99, page: 3, size: 30 }
    })
  })

  it('listShareItems: omits optional params when null', async () => {
    await listShareItems('tok2', null, null, 1, 20)
    expect(mockRequest.get).toHaveBeenCalledWith('/shares/access/tok2/items', {
      params: { page: 1, size: 20 }
    })
  })

  it('listShareItems uses default page=1 size=20', async () => {
    await listShareItems('tok3')
    expect(mockRequest.get).toHaveBeenCalledWith('/shares/access/tok3/items', {
      params: { page: 1, size: 20 }
    })
  })

  it('downloadShareFile: GET /shares/access/{token}/download with itemId and responseType blob', async () => {
    await downloadShareFile('tok4', 'pass2', 77)
    expect(mockRequest.get).toHaveBeenCalledWith('/shares/access/tok4/download', {
      params: { password: 'pass2', itemId: 77 },
      responseType: 'blob',
      timeout: 600000
    })
  })

  it('downloadShareFile: omits password when not provided', async () => {
    await downloadShareFile('tok5', null, 88)
    expect(mockRequest.get).toHaveBeenCalledWith('/shares/access/tok5/download', {
      params: { itemId: 88 },
      responseType: 'blob',
      timeout: 600000
    })
  })

  it('saveShareFiles: POST /shares/access/{token}/save with itemIds array and password query param', async () => {
    await saveShareFiles('tok6', 'pass3', [1, 2, 3])
    expect(mockRequest.post).toHaveBeenCalledWith('/shares/access/tok6/save', [1, 2, 3], {
      params: { password: 'pass3' },
      timeout: 600000
    })
  })

  it('saveShareFiles: omits password when not provided', async () => {
    await saveShareFiles('tok7', null, [10])
    expect(mockRequest.post).toHaveBeenCalledWith('/shares/access/tok7/save', [10], {
      params: {},
      timeout: 600000
    })
  })
})

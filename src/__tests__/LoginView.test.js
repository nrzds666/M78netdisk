import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import LoginView from '@/views/login/LoginView.vue'

describe('LoginView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('should render successfully', () => {
    const wrapper = mount(LoginView, {
      global: {
        stubs: {
          'el-form': { template: '<div><slot/></div>' },
          'el-form-item': { template: '<div><slot/></div>' },
          'el-input': { template: '<div><slot/></div>' },
          'el-button': { template: '<button><slot/></button>' },
          'el-tabs': { template: '<div><slot/></div>' },
          'el-tab-pane': { template: '<div><slot/></div>' },
          'el-alert': { template: '<div><slot/></div>' },
          'el-icon': { template: '<i><slot/></i>' },
          'router-link': { template: '<a><slot/></a>' }
        },
        mocks: {
          $route: { params: {} },
          $router: { push: () => {} }
        }
      }
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('should show login and register tabs', () => {
    const wrapper = mount(LoginView, {
      global: {
        stubs: {
          'el-form': { template: '<div><slot/></div>' },
          'el-form-item': { template: '<div><slot/></div>' },
          'el-input': { template: '<div><slot/></div>' },
          'el-button': { template: '<button><slot/></div>' },
          'el-tabs': { template: '<div><slot/></div>' },
          'el-tab-pane': { template: '<div><slot/></div>' },
          'el-alert': { template: '<div><slot/></div>' },
          'el-icon': { template: '<i><slot/></i>' },
          'router-link': { template: '<a><slot/></a>' }
        },
        mocks: {
          $route: { params: {} },
          $router: { push: () => {} }
        }
      }
    })
    expect(wrapper.text()).toContain('M78')
  })
})

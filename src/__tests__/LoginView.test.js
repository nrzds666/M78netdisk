import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import LoginView from '@/views/login/LoginView.vue'

describe('LoginView', () => {
  it('should render successfully', () => {
    const wrapper = mount(LoginView, {
      global: {
        stubs: {
          'el-form': { template: '<div><slot/></div>' },
          'el-form-item': { template: '<div><slot/></div>' },
          'el-input': { template: '<div><slot/></div>' },
          'el-button': { template: '<button><slot/></button>' },
          'el-tabs': { template: '<div><slot/></div>' },
          'el-tab-pane': { template: '<div><slot/></div>' }
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
          'el-tab-pane': { template: '<div><slot/></div>' }
        }
      }
    })
    expect(wrapper.text()).toContain('M78')
  })
})

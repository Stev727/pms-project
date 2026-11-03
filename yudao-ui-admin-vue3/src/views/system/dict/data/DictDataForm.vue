<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="80px"
    >
      <el-form-item label="字典类型" prop="type">
        <el-input
          v-model="formData.dictType"
          :disabled="typeof formData.id !== 'undefined'"
          placeholder="请输入参数名称"
        />
      </el-form-item>
      <el-form-item label="数据标签" prop="label">
        <el-input v-model="formData.label" placeholder="请输入数据标签" />
      </el-form-item>
      <el-form-item label="数据键值" prop="value">
        <el-input
          v-model="formData.value"
          :placeholder="formType === 'create' ? '留空自动生成（标签拼音首字母）' : '请输入数据键值'"
        />
        <div v-if="formType === 'create'" class="form-tip">
          新增时留空即可，将按数据标签自动生成（如「市场营销」→ scyx）；也可手动修改
        </div>
      </el-form-item>
      <el-form-item label="显示排序" prop="sort">
        <el-input-number v-model="formData.sort" :min="0" controls-position="right" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="formData.status">
          <el-radio
            v-for="dict in getIntDictOptions(DICT_TYPE.COMMON_STATUS)"
            :key="dict.value"
            :value="dict.value"
          >
            {{ dict.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="颜色类型" prop="colorType">
        <el-select filterable v-model="formData.colorType">
          <el-option
            v-for="item in colorTypeOptions"
            :key="item.value"
            :label="item.label + '(' + item.value + ')'"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="CSS Class" prop="cssClass">
        <el-input v-model="formData.cssClass" placeholder="请输入 CSS Class" />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="formData.remark" placeholder="请输入内容" type="textarea" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import * as DictDataApi from '@/api/system/dict/dict.data'
import { CommonStatusEnum } from '@/utils/constants'
import { pinyin } from 'pinyin-pro'

defineOptions({ name: 'SystemDictDataForm' })

const { t } = useI18n() // 国际化
const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const dialogTitle = ref('') // 弹窗的标题
const formLoading = ref(false) // 表单的加载中：1）修改时的数据加载；2）提交的按钮禁用
const formType = ref('') // 表单的类型：create - 新增；update - 修改
const formData = ref<DictDataApi.DictDataVO>({
  id: undefined,
  sort: undefined,
  label: '',
  value: '',
  dictType: '',
  status: CommonStatusEnum.ENABLE,
  colorType: '',
  cssClass: '',
  remark: ''
})
const formRules = reactive({
  label: [{ required: true, message: '数据标签不能为空', trigger: 'blur' }],
  value: [
    {
      // 新增时留空自动生成；编辑时保持必填（改键值有引用断链风险）
      validator: (_rule: any, value: string, callback: any) => {
        if (formType.value === 'update' && !value) {
          callback(new Error('数据键值不能为空'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  sort: [{ required: true, message: '数据顺序不能为空', trigger: 'blur' }],
  status: [{ required: true, message: '状态不能为空', trigger: 'change' }]
})

// ==================== 数据键值自动生成 ====================
// 由数据标签生成键值：汉字取拼音首字母，英文/数字保留并转小写，其余字符跳过。
// 例：「市场营销」→ scyx；「硬件工程师」→ yjgcs；「P2测试」→ p2cs
function autoGenerateValue(label: string): string {
  if (!label) return ''
  const chars = Array.from(label)
  const parts = pinyin(label, { pattern: 'first', toneType: 'none', type: 'array' })
  let result = ''
  chars.forEach((ch, i) => {
    if (/[\u4e00-\u9fa5]/.test(ch)) {
      result += (parts[i] || '').toLowerCase()
    } else if (/[a-zA-Z0-9]/.test(ch)) {
      result += ch.toLowerCase()
    }
  })
  return result
}

// 上一次自动生成的值：用于判断用户是否手动改过键值（手动改过则不再联动覆盖）
const lastAutoValue = ref('')
watch(
  () => formData.value.label,
  (label) => {
    if (formType.value !== 'create') return
    const current = formData.value.value || ''
    if (current && current !== lastAutoValue.value) return // 用户手动填写过，不覆盖
    const gen = autoGenerateValue(label || '')
    formData.value.value = gen
    lastAutoValue.value = gen
  }
)
const formRef = ref() // 表单 Ref

// 数据标签回显样式
const colorTypeOptions = readonly([
  {
    value: 'default',
    label: '默认'
  },
  {
    value: 'primary',
    label: '主要'
  },
  {
    value: 'success',
    label: '成功'
  },
  {
    value: 'info',
    label: '信息'
  },
  {
    value: 'warning',
    label: '警告'
  },
  {
    value: 'danger',
    label: '危险'
  }
])

/** 打开弹窗 */
const open = async (type: string, id?: number, dictType?: string) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  resetForm()
  if (dictType) {
    formData.value.dictType = dictType
  }
  // 修改时，设置数据
  if (id) {
    formLoading.value = true
    try {
      formData.value = await DictDataApi.getDictData(id)
    } finally {
      formLoading.value = false
    }
  }
}
defineExpose({ open }) // 提供 open 方法，用于打开弹窗

/** 提交表单 */
const emit = defineEmits(['success']) // 定义 success 事件，用于操作成功后的回调
const submitForm = async () => {
  // 校验表单
  if (!formRef) return
  const valid = await formRef.value.validate()
  if (!valid) return
  // 新增时键值留空 → 按标签自动生成兜底（watch 未触发的边缘场景）
  if (formType.value === 'create' && !formData.value.value) {
    formData.value.value = autoGenerateValue(formData.value.label || '')
  }
  // 生成后仍为空（标签无有效字符）则提示
  if (!formData.value.value) {
    message.error('无法自动生成数据键值，请手动填写')
    return
  }
  // 提交请求
  formLoading.value = true
  try {
    const data = formData.value
    if (formType.value === 'create') {
      await DictDataApi.createDictData(data)
      message.success(t('common.createSuccess'))
    } else {
      await DictDataApi.updateDictData(data)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    // 发送操作成功的事件
    emit('success')
  } finally {
    formLoading.value = false
  }
}

/** 重置表单 */
const resetForm = () => {
  formData.value = {
    id: undefined,
    sort: undefined,
    label: '',
    value: '',
    dictType: '',
    status: CommonStatusEnum.ENABLE,
    colorType: '',
    cssClass: '',
    remark: ''
  }
  lastAutoValue.value = ''
  formRef.value?.resetFields()
}
</script>

<style scoped>
.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
  margin-top: 2px;
}
</style>

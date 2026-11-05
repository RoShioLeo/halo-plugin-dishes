<script setup lang="ts">
import { ref } from 'vue'

defineProps<{ offMenu: string; remark: string; disabled: boolean }>()
const emit = defineEmits<{
  'update:offMenu': [value: string]
  'update:remark': [value: string]
  blur: []
}>()
const offMenuInput = ref<HTMLTextAreaElement | null>(null)
const remarkInput = ref<HTMLTextAreaElement | null>(null)
defineExpose({
  field: (name: string) => name === 'offMenu' ? offMenuInput.value : remarkInput.value,
})
</script>

<template>
  <div class="k-supplement-editor">
    <label class="k-field"><span>想吃其他菜</span><textarea ref="offMenuInput" data-k-supplement-field="offMenu" :value="offMenu" :disabled="disabled" maxlength="1000" rows="3" @input="emit('update:offMenu', ($event.target as HTMLTextAreaElement).value)" @blur="emit('blur')" /></label>
    <label class="k-field"><span>本餐备注</span><textarea ref="remarkInput" data-k-supplement-field="remark" :value="remark" :disabled="disabled" maxlength="500" rows="2" @input="emit('update:remark', ($event.target as HTMLTextAreaElement).value)" @blur="emit('blur')" /></label>
  </div>
</template>

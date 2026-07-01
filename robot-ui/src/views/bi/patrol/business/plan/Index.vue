<template>
  <TabTableList v-if="!id" @close="showEdit" />
  <Edit v-else @close="showEdit('')" :id="id" />
</template>

<script>
import Edit from './Edit.vue';
import TabTableList from './TaskTableList.vue'
export default {
  name: 'BiPatrolBusinessPlan',
  components: { TabTableList, Edit },
  data() {
    return {
      id: ''
    }
  },
  methods: {
    showEdit(id) {
      this.isListMode = id ? false : true;
      this.id = id;
    }
  }
}
</script>

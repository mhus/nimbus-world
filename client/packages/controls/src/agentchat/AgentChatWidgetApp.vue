<template>
  <div class="h-screen flex flex-col bg-base-200 overflow-hidden">
    <!-- Header -->
    <header v-if="!isEmbedded()" class="navbar bg-base-300 shadow-lg">
      <div class="flex-none">
        <a href="/controls/index.html" class="btn btn-ghost btn-square">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
          </svg>
        </a>
      </div>
      <div class="flex-1">
        <h1 class="text-xl font-bold px-4">Agent Chat</h1>
      </div>
      <div class="flex-none">
        <span v-if="worldId && playerId" class="text-sm text-base-content/70">
          {{ worldId }} / {{ playerId }}
        </span>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 px-4 py-4 overflow-hidden">
      <!-- Missing Parameters -->
      <div v-if="!worldId || !playerId" class="flex items-center justify-center min-h-[400px]">
        <div class="alert alert-warning max-w-md">
          <svg xmlns="http://www.w3.org/2000/svg" class="stroke-current shrink-0 h-6 w-6" fill="none" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          <span>Missing worldId or playerId parameters.</span>
        </div>
      </div>

      <!-- Main Chat Interface -->
      <div v-else class="max-w-6xl mx-auto h-full flex flex-col">
        <!-- Error Display -->
        <div v-if="error" class="alert alert-error mb-4">
          <svg xmlns="http://www.w3.org/2000/svg" class="stroke-current shrink-0 h-6 w-6" fill="none" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span>{{ error }}</span>
        </div>

        <!-- Chat Layout with Collapsible Sidebar -->
        <div class="relative flex-1 overflow-hidden">
          <!-- Overlay (click to close sidebar) -->
          <div
            v-if="showChatList"
            @click="showChatList = false"
            class="absolute inset-0 bg-black/30 z-10 transition-opacity duration-300"
          ></div>

          <!-- Chat List Sidebar (collapsible) -->
          <div
            class="absolute left-0 top-0 bottom-0 w-80 bg-base-100 shadow-xl z-20 transition-transform duration-300 overflow-y-auto rounded-r-lg"
            :class="showChatList ? 'translate-x-0' : '-translate-x-full'"
          >
            <div class="card bg-base-100 shadow-lg">
              <div class="card-body">
                <div class="flex justify-between items-center mb-4">
                  <h2 class="card-title text-lg">Your Chats</h2>
                  <button
                    @click="showNewChatDialog = true"
                    class="btn btn-primary btn-sm"
                    :disabled="loading"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
                    </svg>
                    New Chat
                  </button>
                </div>

                <!-- Loading State -->
                <div v-if="loading && chats.length === 0" class="flex justify-center py-8">
                  <span class="loading loading-spinner loading-lg"></span>
                </div>

                <!-- Chat List -->
                <div v-else-if="chats.length > 0" class="space-y-2">
                  <div
                    v-for="chat in chats"
                    :key="chat.chatId"
                    @click="selectChat(chat)"
                    class="p-3 rounded-lg cursor-pointer transition-colors"
                    :class="{
                      'bg-primary text-primary-content': selectedChat?.chatId === chat.chatId,
                      'bg-base-200 hover:bg-base-300': selectedChat?.chatId !== chat.chatId
                    }"
                  >
                    <div class="font-semibold">{{ chat.name }}</div>
                    <div class="text-xs opacity-70">
                      {{ chat.type }} • {{ formatDate(chat.createdAt) }}
                    </div>
                  </div>
                </div>

                <!-- Empty State -->
                <div v-else class="text-center py-8 text-base-content/50">
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-12 w-12 mx-auto mb-2 opacity-50" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
                  </svg>
                  <p>No chats yet</p>
                  <p class="text-sm">Click "New Chat" to start</p>
                </div>
              </div>
            </div>
          </div>

          <!-- Main Chat Area (full width) -->
          <div class="w-full h-full">
            <div class="card bg-base-100 shadow-lg h-full flex flex-col">
              <div class="card-body flex flex-col h-full p-0">
                <!-- Chat Header -->
                <div class="p-4 border-b border-base-300 flex-none">
                  <div class="flex justify-between items-center">
                    <div class="flex items-center gap-3">
                      <!-- Toggle Chat List Button -->
                      <button
                        @click="showChatList = !showChatList"
                        class="btn btn-ghost btn-sm btn-square"
                        title="Toggle chat list"
                      >
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
                        </svg>
                      </button>
                      <div v-if="selectedChat">
                        <h2 class="text-xl font-bold">{{ selectedChat.name }}</h2>
                        <div class="text-sm text-base-content/70">{{ selectedChat.type }}</div>
                      </div>
                      <div v-else class="text-base-content/50">
                        Select a chat to start
                      </div>
                    </div>
                    <button
                      v-if="selectedChat"
                      @click="archiveChat(selectedChat.chatId)"
                      class="btn btn-ghost btn-sm"
                      title="Archive chat"
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 8h14M5 8a2 2 0 110-4h14a2 2 0 110 4M5 8v10a2 2 0 002 2h10a2 2 0 002-2V8m-9 4h4" />
                      </svg>
                    </button>
                  </div>
                </div>

                <!-- Messages Area -->
                <div class="flex-1 overflow-y-auto p-4 space-y-3" ref="messagesContainer">
                  <!-- Loading Messages -->
                  <div v-if="loadingMessages" class="flex justify-center py-8">
                    <span class="loading loading-spinner loading-lg"></span>
                  </div>

                  <!-- Messages -->
                  <div v-else-if="messages.length > 0" class="space-y-3">
                    <div
                      v-for="message in messages"
                      :key="message.messageId"
                    >
                      <!-- Command messages as buttons -->
                      <div v-if="message.command" class="flex justify-center my-2">
                        <button
                          @click="executeCommand(message.messageId, message.type)"
                          class="btn btn-secondary btn-sm"
                          :disabled="executing"
                        >
                          <span v-if="executing" class="loading loading-spinner loading-xs mr-1"></span>
                          {{ message.type }}
                        </button>
                      </div>

                      <!-- Regular chat messages -->
                      <div
                        v-else
                        class="chat"
                        :class="message.senderId === playerId ? 'chat-end' : 'chat-start'"
                      >
                        <div class="chat-header mb-1">
                          {{ message.senderId === playerId ? 'You' : message.senderId }}
                          <time class="text-xs opacity-50 ml-1">{{ formatTime(message.createdAt) }}</time>
                        </div>
                        <div
                          class="chat-bubble"
                          :class="{
                            'chat-bubble-primary': message.senderId === playerId,
                            'chat-bubble-error': message.type === 'error',
                            'chat-bubble-secondary': message.senderId !== playerId && message.type !== 'error'
                          }"
                        >
                          {{ message.message }}
                        </div>
                      </div>
                    </div>
                  </div>

                  <!-- Empty State -->
                  <div v-else-if="selectedChat" class="flex items-center justify-center h-full text-base-content/50">
                    <div class="text-center">
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-16 w-16 mx-auto mb-3 opacity-50" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
                      </svg>
                      <p>No messages yet</p>
                      <p class="text-sm">Start a conversation below</p>
                    </div>
                  </div>

                  <!-- No Chat Selected -->
                  <div v-else class="flex items-center justify-center h-full text-base-content/50">
                    <div class="text-center">
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-16 w-16 mx-auto mb-3 opacity-50" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 8h10M7 12h4m1 8l-4-4H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-3l-4 4z" />
                      </svg>
                      <p>Select a chat to start</p>
                    </div>
                  </div>
                </div>

                <!-- Message Input -->
                <div v-if="selectedChat" class="p-4 border-t border-base-300 flex-none">
                  <form @submit.prevent="sendMessage" class="flex gap-2">
                    <input
                      v-model="newMessage"
                      type="text"
                      placeholder="Type your message..."
                      class="input input-bordered flex-1"
                      :disabled="sending"
                    />
                    <button
                      type="submit"
                      class="btn btn-primary"
                      :disabled="!newMessage.trim() || sending"
                    >
                      <span v-if="sending" class="loading loading-spinner loading-sm"></span>
                      <svg v-else xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
                      </svg>
                    </button>
                  </form>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>

    <!-- New Chat Dialog -->
    <dialog :open="showNewChatDialog" class="modal">
      <div class="modal-box">
        <h3 class="font-bold text-lg mb-4">Create New Chat</h3>

        <form @submit.prevent="createChat" class="space-y-4">
          <!-- Chat Title -->
          <div class="form-control">
            <label class="label">
              <span class="label-text">Chat Title</span>
            </label>
            <input
              v-model="newChatTitle"
              type="text"
              placeholder="Enter chat title"
              class="input input-bordered"
              required
            />
          </div>

          <!-- Select Agent -->
          <div class="form-control">
            <label class="label">
              <span class="label-text">Select Agent</span>
            </label>
            <select v-model="selectedAgent" class="select select-bordered" required>
              <option value="">-- Select an agent --</option>
              <option v-for="agent in availableAgents" :key="agent.name" :value="agent.name">
                {{ agent.title }}
              </option>
            </select>
          </div>

          <!-- Model Selector (optional) -->
          <div class="form-control">
            <label class="label">
              <span class="label-text">Model (optional)</span>
            </label>
            <input
              v-model="newChatModel"
              type="text"
              placeholder="e.g., gpt-4"
              class="input input-bordered"
            />
          </div>

          <!-- Actions -->
          <div class="modal-action">
            <button
              type="button"
              class="btn"
              @click="closeNewChatDialog"
              :disabled="creating"
            >
              Cancel
            </button>
            <button
              type="submit"
              class="btn btn-primary"
              :disabled="!newChatTitle.trim() || !selectedAgent || creating"
            >
              <span v-if="creating" class="loading loading-spinner loading-sm mr-2"></span>
              Create
            </button>
          </div>
        </form>
      </div>
      <form method="dialog" class="modal-backdrop">
        <button @click="closeNewChatDialog">close</button>
      </form>
    </dialog>

    <!-- Archive Chat Confirmation Dialog -->
    <dialog :open="showArchiveDialog" class="modal">
      <div class="modal-box">
        <h3 class="font-bold text-lg mb-4">Archive Chat</h3>
        <p class="py-4">Are you sure you want to archive this chat?</p>
        <div class="modal-action">
          <button
            type="button"
            class="btn"
            @click="cancelArchive"
          >
            Cancel
          </button>
          <button
            type="button"
            class="btn btn-error"
            @click="confirmArchive"
          >
            Archive
          </button>
        </div>
      </div>
      <form method="dialog" class="modal-backdrop">
        <button @click="cancelArchive">close</button>
      </form>
    </dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue';
import { useModal } from '@/composables/useModal';
import { apiService } from '@/services/ApiService';

// Types
interface Chat {
  chatId: string;
  name: string;
  type: string;
  createdAt: string;
  modifiedAt: string;
  archived: boolean;
  ownerId: string;
  model?: string;
}

interface Message {
  messageId: string;
  senderId: string;
  message: string;
  type: string;
  command: boolean;
  createdAt: string;
}

interface Agent {
  name: string;
  title: string;
}

// Modal composable for embedded detection
const { isEmbedded } = useModal();

// Get API base URL
const apiBaseUrl = computed(() => apiService.getBaseUrl());

// URL Parameters
const urlParams = new URLSearchParams(window.location.search);
const worldId = ref(urlParams.get('worldId') || '');
const playerId = ref(urlParams.get('playerId') || '');

// Polling configuration
const POLL_INTERVAL_MS = 3000; // Poll every 3 seconds
let pollingInterval: number | null = null;

// State
const chats = ref<Chat[]>([]);
const selectedChat = ref<Chat | null>(null);
const messages = ref<Message[]>([]);
const availableAgents = ref<Agent[]>([]);
const loading = ref(false);
const loadingMessages = ref(false);
const sending = ref(false);
const creating = ref(false);
const executing = ref(false);
const error = ref('');
const newMessage = ref('');
const showNewChatDialog = ref(false);
const newChatTitle = ref('');
const selectedAgent = ref('');
const newChatModel = ref('');
const messagesContainer = ref<HTMLElement | null>(null);
const showChatList = ref(true); // Show by default
const showArchiveDialog = ref(false);
const chatToArchive = ref<string | null>(null);

// Load chats
const loadChats = async () => {
  if (!worldId.value || !playerId.value) return;

  loading.value = true;
  error.value = '';

  try {
    const response = await fetch(
      `${apiBaseUrl.value}/control/player/chats/${worldId.value}/${playerId.value}`,
      {
        credentials: 'include',
      }
    );

    if (!response.ok) {
      throw new Error(`Failed to load chats: ${response.statusText}`);
    }

    chats.value = await response.json();
  } catch (e: any) {
    error.value = e.message;
    console.error('Error loading chats:', e);
  } finally {
    loading.value = false;
  }
};

// Select chat
const selectChat = async (chat: Chat) => {
  // Stop polling for previous chat
  stopPolling();

  selectedChat.value = chat;
  await loadMessages(chat.chatId);

  // Start polling for new messages
  startPolling();

  // Auto-hide chat list when a chat is selected
  showChatList.value = false;
};

// Load messages
const loadMessages = async (chatId: string) => {
  if (!worldId.value) return;

  loadingMessages.value = true;
  error.value = '';

  try {
    const response = await fetch(
      `${apiBaseUrl.value}/control/player/chats/${worldId.value}/${chatId}/messages?limit=50`,
      {
        credentials: 'include',
      }
    );

    if (!response.ok) {
      throw new Error(`Failed to load messages: ${response.statusText}`);
    }

    messages.value = await response.json();

    // Scroll to bottom after messages are rendered
    await nextTick();
    setTimeout(() => {
      if (messagesContainer.value) {
        messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
      }
    }, 50);
  } catch (e: any) {
    error.value = e.message;
    console.error('Error loading messages:', e);
  } finally {
    loadingMessages.value = false;
  }
};

// Poll for new messages
const pollNewMessages = async () => {
  if (!selectedChat.value || !worldId.value || messages.value.length === 0) return;

  try {
    // Get the messageId of the last message
    const lastMessage = messages.value[messages.value.length - 1];
    const afterMessageId = lastMessage.messageId;

    const response = await fetch(
      `${apiBaseUrl.value}/control/player/chats/${worldId.value}/${selectedChat.value.chatId}/messages?afterMessageId=${afterMessageId}`,
      {
        credentials: 'include',
      }
    );

    if (!response.ok) {
      // Don't set error for polling failures to avoid disrupting the user
      console.warn('Failed to poll for new messages:', response.statusText);
      return;
    }

    const newMessages: Message[] = await response.json();

    // Add new messages to the list if any were returned
    if (newMessages.length > 0) {
      messages.value.push(...newMessages);

      // Scroll to bottom after new messages are rendered
      await nextTick();
      setTimeout(() => {
        if (messagesContainer.value) {
          messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
        }
      }, 50);
    }
  } catch (e: any) {
    // Don't set error for polling failures to avoid disrupting the user
    console.warn('Error polling for new messages:', e);
  }
};

// Start polling for new messages
const startPolling = () => {
  stopPolling(); // Clear any existing interval
  pollingInterval = window.setInterval(pollNewMessages, POLL_INTERVAL_MS);
};

// Stop polling for new messages
const stopPolling = () => {
  if (pollingInterval !== null) {
    clearInterval(pollingInterval);
    pollingInterval = null;
  }
};

// Send message
const sendMessage = async () => {
  if (!selectedChat.value || !newMessage.value.trim()) return;

  sending.value = true;
  error.value = '';

  try {
    const response = await fetch(
      `${apiBaseUrl.value}/control/player/chats/${worldId.value}/${selectedChat.value.chatId}/messages/${playerId.value}`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include',
        body: JSON.stringify({
          message: newMessage.value,
        }),
      }
    );

    if (!response.ok) {
      throw new Error(`Failed to send message: ${response.statusText}`);
    }

    // Clear input
    newMessage.value = '';

    // Reload messages
    await loadMessages(selectedChat.value.chatId);
  } catch (e: any) {
    error.value = e.message;
    console.error('Error sending message:', e);
  } finally {
    sending.value = false;
  }
};

// Execute command
const executeCommand = async (messageId: string, type: string) => {
  if (!selectedChat.value) return;

  executing.value = true;
  error.value = '';

  try {
    const response = await fetch(
      `${apiBaseUrl.value}/control/player/chats/${worldId.value}/${selectedChat.value.chatId}/execute-command/${playerId.value}`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include',
        body: JSON.stringify({
          command: type,
          params: {
            messageId,
            chatId: selectedChat.value.chatId
          },
        }),
      }
    );

    if (!response.ok) {
      throw new Error(`Failed to execute command: ${response.statusText}`);
    }

    // Reload messages to show command results
    await loadMessages(selectedChat.value.chatId);
  } catch (e: any) {
    error.value = e.message;
    console.error('Error executing command:', e);
  } finally {
    executing.value = false;
  }
};

// Load available agents
const loadAgents = async () => {
  try {
    const response = await fetch(`${apiBaseUrl.value}/control/player/chats/agents`, {
      credentials: 'include',
    });

    if (!response.ok) {
      throw new Error(`Failed to load agents: ${response.statusText}`);
    }

    availableAgents.value = await response.json();
  } catch (e: any) {
    console.error('Error loading agents:', e);
  }
};

// Create chat
const createChat = async () => {
  if (!newChatTitle.value.trim() || !selectedAgent.value) return;

  creating.value = true;
  error.value = '';

  try {
    const response = await fetch(
      `${apiBaseUrl.value}/control/player/chats/${worldId.value}/${playerId.value}`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include',
        body: JSON.stringify({
          name: newChatTitle.value,
          type: selectedAgent.value,
          agentName: selectedAgent.value,
          model: newChatModel.value || null,
        }),
      }
    );

    if (!response.ok) {
      throw new Error(`Failed to create chat: ${response.statusText}`);
    }

    const newChat = await response.json();

    // Close dialog
    closeNewChatDialog();

    // Reload chats
    await loadChats();

    // Select new chat
    await selectChat(newChat);
  } catch (e: any) {
    error.value = e.message;
    console.error('Error creating chat:', e);
  } finally {
    creating.value = false;
  }
};

// Close new chat dialog
const closeNewChatDialog = () => {
  showNewChatDialog.value = false;
  newChatTitle.value = '';
  selectedAgent.value = '';
  newChatModel.value = '';
};

// Archive chat - show confirmation dialog
const archiveChat = (chatId: string) => {
  chatToArchive.value = chatId;
  showArchiveDialog.value = true;
};

// Confirm archive - actually archive the chat
const confirmArchive = async () => {
  if (!chatToArchive.value) return;

  const chatId = chatToArchive.value;
  showArchiveDialog.value = false;
  chatToArchive.value = null;

  try {
    const response = await fetch(
      `${apiBaseUrl.value}/control/player/chats/${worldId.value}/${chatId}/archive`,
      {
        method: 'PUT',
        credentials: 'include',
      }
    );

    if (!response.ok) {
      throw new Error(`Failed to archive chat: ${response.statusText}`);
    }

    // Reload chats
    await loadChats();

    // Clear selection if archived chat was selected
    if (selectedChat.value?.chatId === chatId) {
      stopPolling();
      selectedChat.value = null;
      messages.value = [];
    }

    // Show chat list again
    showChatList.value = true;
  } catch (e: any) {
    error.value = e.message;
    console.error('Error archiving chat:', e);
  }
};

// Cancel archive
const cancelArchive = () => {
  showArchiveDialog.value = false;
  chatToArchive.value = null;
};

// Format date
const formatDate = (dateString: string) => {
  const date = new Date(dateString);
  return date.toLocaleDateString();
};

// Format time
const formatTime = (dateString: string) => {
  const date = new Date(dateString);
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
};

// Initialize
onMounted(async () => {
  await loadChats();
  await loadAgents();

  // Show chat list if no chat is selected or no chats exist
  if (!selectedChat.value || chats.value.length === 0) {
    showChatList.value = true;
  }
});

// Cleanup
onUnmounted(() => {
  stopPolling();
});
</script>

<style scoped>
/* Custom scrollbar for messages */
.overflow-y-auto::-webkit-scrollbar {
  width: 8px;
}

.overflow-y-auto::-webkit-scrollbar-track {
  background: transparent;
}

.overflow-y-auto::-webkit-scrollbar-thumb {
  background: hsl(var(--bc) / 0.2);
  border-radius: 4px;
}

.overflow-y-auto::-webkit-scrollbar-thumb:hover {
  background: hsl(var(--bc) / 0.3);
}
</style>
